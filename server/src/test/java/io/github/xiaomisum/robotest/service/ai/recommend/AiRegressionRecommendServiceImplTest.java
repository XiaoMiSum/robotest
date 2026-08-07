package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRegressionRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRegressionRecommendRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiKeywordExtractor;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementContextAssembler;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService.CaseDedupHit;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试子集推荐服务单测（详细设计 3.5 / 4.5）：
 * 三态输入校验、saveAsRequirement 不阻断、模块精确/模糊匹配（含子孙目录收集）、
 * 语义 TopK 与降级关键词分支、both 合并取高分、理由长度不匹配/失败整体置空、50 条截断。
 */
@ExtendWith(MockitoExtension.class)
class AiRegressionRecommendServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DIR_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private AiVectorSearchService vectorSearchService;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private AiRequirementContextAssembler requirementContextAssembler;
    @Mock
    private AiKeywordExtractor aiKeywordExtractor;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<ChatCallOptions> optionsCaptor;

    @InjectMocks
    private AiRegressionRecommendServiceImpl service;

    @BeforeEach
    void stubDefaultRequirementContext() {
        // matchSemantic 对空 changeData 提前短路，默认返回非空变更描述；需要特定内容的测试单独覆盖
        lenient().when(requirementContextAssembler.assemble(any(), any(), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext("变更描述\n", List.of()));
    }

    private AiRegressionRecommendReqDTO req(List<String> modules, String text, List<UUID> requirementIds) {
        AiRegressionRecommendReqDTO dto = new AiRegressionRecommendReqDTO();
        dto.setModules(modules);
        dto.setText(text);
        dto.setRequirementIds(requirementIds);
        return dto;
    }

    private TestCaseModule module(UUID id, UUID parentId, String type, String name) {
        TestCaseModule module = new TestCaseModule();
        module.setId(id);
        module.setProjectId(PROJECT_ID);
        module.setParentId(parentId);
        module.setType(type);
        module.setName(name);
        return module;
    }

    private TestCaseModule directory() {
        return module(DIR_ID, null, Constants.ModuleType.DIRECTORY, "登录模块");
    }

    private TestCaseModule document() {
        return module(DOC_ID, DIR_ID, Constants.ModuleType.DOCUMENT, "验证码登录");
    }

    private TestCaseNode caseNode(UUID id, String title) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setDocumentId(DOC_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle(title);
        return node;
    }

    private AiStatusRespDTO status(String semanticSearch) {
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setSemanticSearch(semanticSearch);
        return status;
    }

    private AiRegressionRecommendServiceImpl.ReasonOut reasonsOut(int size) {
        AiRegressionRecommendServiceImpl.ReasonOut out =
                new AiRegressionRecommendServiceImpl.ReasonOut();
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            reasons.add("涉及登录核心链路，回归需覆盖");
        }
        out.setReasons(reasons);
        return out;
    }

    private void stubReasonOut(int size) {
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REGRESSION_RECOMMENDATION), any(),
                any(), any(), eq(AiRegressionRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(size));
    }

    @Test
    void allInputsEmpty_throws() {
        assertThrows(ServiceException.class,
                () -> service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req(null, null, null)));
    }

    @Test
    void saveAsRequirementWithoutText_throws() {
        AiRegressionRecommendReqDTO dto = req(List.of("登录模块"), null, null);
        AiRegressionRecommendReqDTO.SaveAsRequirement saveAs =
                new AiRegressionRecommendReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);
        assertThrows(ServiceException.class,
                () -> service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void moduleExactMatch_scores1_returnsModuleCandidates() {
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        stubReasonOut(1);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), null, null));

        assertEquals(1, resp.getItems().size());
        assertEquals("module", resp.getItems().get(0).getMatchType());
        assertEquals(1.0, resp.getItems().get(0).getScore());
        assertEquals("登录模块/验证码登录", resp.getItems().get(0).getModulePath());
        assertEquals("验证码登录成功", resp.getItems().get(0).getTitle());
        // 仅模块输入不触发语义检索（无 text/requirementIds）
        assertTrue(!resp.isSemanticDegraded());
        verify(vectorSearchService, never()).searchSimilarCases(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void moduleFuzzyMatch_scores09() {
        // 模块名「登录模块V2」包含输入名称「登录模块」→ ILIKE 模糊命中 0.9
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID))
                .thenReturn(List.of(module(DIR_ID, null, Constants.ModuleType.DIRECTORY, "登录模块V2"), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        stubReasonOut(1);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), null, null));

        assertEquals("module", resp.getItems().get(0).getMatchType());
        assertEquals(0.9, resp.getItems().get(0).getScore());
    }

    @Test
    void semanticAvailable_vectorTopK_scoresSimilarity() {
        UUID nodeId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("regression.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("regression.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCases(eq(PROJECT_ID), anyString(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeId, 0.85)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeId)))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        stubReasonOut(1);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(null, "支持手机号验证码登录", null));

        assertTrue(!resp.isSemanticDegraded());
        assertEquals("semantic", resp.getItems().get(0).getMatchType());
        assertEquals(0.85, resp.getItems().get(0).getScore());
    }

    @Test
    void semanticUnavailable_degradedKeyword_scores06() {
        UUID nodeId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("验证码"));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "验证码", 30))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        stubReasonOut(1);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(null, "支持手机号验证码登录", null));

        assertTrue(resp.isSemanticDegraded());
        assertEquals("semantic", resp.getItems().get(0).getMatchType());
        assertEquals(0.6, resp.getItems().get(0).getScore());
    }

    @Test
    void bothModuleAndSemantic_mergeToBoth_takesHigherScore() {
        UUID nodeId = UUID.randomUUID();
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("regression.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("regression.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCases(eq(PROJECT_ID), anyString(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeId, 0.85)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeId)))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        stubReasonOut(1);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), "支持手机号验证码登录", null));

        // 双命中：模块精确 1.0 > 语义 0.85，取高分，matchType 合并为 both
        assertEquals(1, resp.getItems().size());
        assertEquals("both", resp.getItems().get(0).getMatchType());
        assertEquals(1.0, resp.getItems().get(0).getScore());
    }

    @Test
    void reasonLengthMismatch_reasonsEmpty() {
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        // 理由数组长度（2）与清单（1）不一致 → 整体置空
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REGRESSION_RECOMMENDATION), any(),
                any(), optionsCaptor.capture(), eq(AiRegressionRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(2));

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), null, null));

        assertNull(resp.getItems().get(0).getReason());
        // 理由生成读超时功能级覆盖 60s（4.5 步骤 4）
        assertEquals(60_000, optionsCaptor.getValue().readTimeoutMillis());
    }

    @Test
    void reasonCallFails_reasonsEmpty() {
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REGRESSION_RECOMMENDATION), any(),
                any(), any(), eq(AiRegressionRecommendServiceImpl.ReasonOut.class), any()))
                .thenThrow(new ServiceException());

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), null, null));

        // 理由生成失败不阻断清单返回（4.5）
        assertEquals(1, resp.getItems().size());
        assertNull(resp.getItems().get(0).getReason());
    }

    @Test
    void requirementItems_appendedToChangeData() {
        UUID reqId = UUID.randomUUID();
        when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "【需求条目】登录需求\n用户可通过邮箱与密码登录\n", List.of()));
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("登录"));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REGRESSION_RECOMMENDATION), any(),
                businessDataCaptor.capture(), any(), eq(AiRegressionRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(1));

        service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req(null, null, List.of(reqId)));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【需求条目】登录需求"));
        assertTrue(data.contains("用户可通过邮箱与密码登录"));
        // 变更块进入语义向量化与理由生成的公共输入
        assertTrue(data.contains("【用例标题清单】"));
    }

    @Test
    void candidatesOverLimit_truncatesTo50() {
        List<TestCaseNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 55; i++) {
            nodes.add(caseNode(UUID.randomUUID(), "用例" + i));
        }
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection())).thenReturn(nodes);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(List.of("登录模块"), null, null));

        // 截断 50 条（3.5）
        assertEquals(50, resp.getItems().size());
    }

    @Test
    void saveAsRequirement_savesItemBeforeRecommend() {
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("验证码"));
        // 降级关键词路径文档为空 → 语义候选为空，仅剩模块命中
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(requirementContextAssembler.trySaveRequirement(eq(PROJECT_ID), eq(USER_ID), any(), any()))
                .thenReturn(true);
        stubReasonOut(1);
        AiRegressionRecommendReqDTO dto = req(List.of("登录模块"), "需求：支持手机号验证码登录", null);
        AiRegressionRecommendReqDTO.SaveAsRequirement saveAs =
                new AiRegressionRecommendReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);

        service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        verify(requirementContextAssembler).trySaveRequirement(eq(PROJECT_ID), eq(USER_ID),
                eq("登录需求"), eq("需求：支持手机号验证码登录"));
    }

    @Test
    void saveAsRequirementSaveFails_continuesRecommend() {
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("验证码"));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(anyCollection()))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(requirementContextAssembler.trySaveRequirement(any(), any(), any(), any()))
                .thenReturn(false);
        stubReasonOut(1);
        AiRegressionRecommendReqDTO dto = req(List.of("登录模块"), "需求：支持手机号验证码登录", null);
        AiRegressionRecommendReqDTO.SaveAsRequirement saveAs =
                new AiRegressionRecommendReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);

        AiRegressionRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        // 保存失败不阻断推荐（3.5）
        assertEquals(1, resp.getItems().size());
    }
}
