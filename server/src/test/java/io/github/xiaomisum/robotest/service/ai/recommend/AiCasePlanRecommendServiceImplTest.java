package io.github.xiaomisum.robotest.service.ai.recommend;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCasePlanRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiCasePlanRecommendRespDTO;
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
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService.CaseDedupHit;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用例规划智能推荐服务单测（详细设计 3.5 / 4.5）：
 * 输入校验、语义 TopK 与降级关键词分支、排除已纳入用例、理由长度不匹配/失败整体置空、50 条截断。
 */
@ExtendWith(MockitoExtension.class)
class AiCasePlanRecommendServiceImplTest {

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
    private AiCasePlanRecommendServiceImpl service;

    @BeforeEach
    void stubDefaultRequirementContext() {
        // matchSemantic 对空 needData 提前短路，默认返回非空需求描述与检索块；需要特定内容的测试单独覆盖
        lenient().when(requirementContextAssembler.assemble(any(), any(), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "需求描述\n", List.of(), List.of("需求描述\n")));
    }

    private AiCasePlanRecommendReqDTO req(String text, List<UUID> requirementIds, List<UUID> excludeCaseNodeIds) {
        AiCasePlanRecommendReqDTO dto = new AiCasePlanRecommendReqDTO();
        dto.setText(text);
        dto.setRequirementIds(requirementIds);
        dto.setExcludeCaseNodeIds(excludeCaseNodeIds);
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

    private AiCasePlanRecommendServiceImpl.ReasonOut reasonsOut(int size) {
        AiCasePlanRecommendServiceImpl.ReasonOut out =
                new AiCasePlanRecommendServiceImpl.ReasonOut();
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            reasons.add("覆盖需求核心链路，建议纳入");
        }
        out.setReasons(reasons);
        return out;
    }

    private void stubReasonOut(int size) {
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.CASE_PLAN_RECOMMENDATION), any(),
                any(), any(), eq(AiCasePlanRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(size));
    }

    @Test
    void allInputsEmpty_throws() {
        assertThrows(ServiceException.class,
                () -> service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req(null, null, null)));
    }

    @Test
    void semanticAvailable_vectorTopK_scoresSimilarity() {
        UUID nodeId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), anyList(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeId, 0.85)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeId)))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        stubReasonOut(1);

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, null));

        assertTrue(!resp.isSemanticDegraded());
        assertEquals("semantic", resp.getItems().get(0).getMatchType());
        assertEquals(0.85, resp.getItems().get(0).getScore());
        assertEquals("登录模块/验证码登录", resp.getItems().get(0).getModulePath());
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

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, null));

        assertTrue(resp.isSemanticDegraded());
        assertEquals("semantic", resp.getItems().get(0).getMatchType());
        assertEquals(0.6, resp.getItems().get(0).getScore());
    }

    @Test
    void excludeCaseNodeIds_filteredOut() {
        UUID excludedId = UUID.randomUUID();
        UUID keptId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), anyList(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(excludedId, 0.9), new CaseDedupHit(keptId, 0.8)));
        when(testCaseNodeMapper.selectByIds(List.of(excludedId, keptId)))
                .thenReturn(List.of(caseNode(excludedId, "已纳入用例"), caseNode(keptId, "待推荐用例")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        stubReasonOut(1);

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, List.of(excludedId)));

        // 排除已纳入当前评审/计划的用例（4.5 步骤 2）
        assertEquals(1, resp.getItems().size());
        assertEquals(keptId, resp.getItems().get(0).getCaseNodeId());
    }

    @Test
    void reasonLengthMismatch_reasonsEmpty() {
        UUID nodeId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), anyList(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeId, 0.85)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeId)))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        // 理由数组长度（2）与清单（1）不一致 → 整体置空
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.CASE_PLAN_RECOMMENDATION), any(),
                any(), optionsCaptor.capture(), eq(AiCasePlanRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(2));

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, null));

        assertNull(resp.getItems().get(0).getReason());
        // 理由生成读超时功能级覆盖 60s（4.5 步骤 3）
        assertEquals(60_000, optionsCaptor.getValue().readTimeoutMillis());
    }

    @Test
    void reasonCallFails_reasonsEmpty() {
        UUID nodeId = UUID.randomUUID();
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), anyList(), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeId, 0.85)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeId)))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.CASE_PLAN_RECOMMENDATION), any(),
                any(), any(), eq(AiCasePlanRecommendServiceImpl.ReasonOut.class), any()))
                .thenThrow(new ServiceException());

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, null));

        // 理由生成失败不阻断清单返回（4.5）
        assertEquals(1, resp.getItems().size());
        assertNull(resp.getItems().get(0).getReason());
    }

    @Test
    void requirementItems_appendedToNeedData() {
        UUID reqId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "【需求条目】登录需求\n用户可通过邮箱与密码登录\n",
                        List.of(),
                        List.of("【需求条目】登录需求\n用户可通过邮箱与密码登录\n")));
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("登录"));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(nodeId, "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.CASE_PLAN_RECOMMENDATION), any(),
                businessDataCaptor.capture(), any(), eq(AiCasePlanRecommendServiceImpl.ReasonOut.class), any()))
                .thenReturn(reasonsOut(1));

        service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req(null, List.of(reqId), null));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【需求条目】登录需求"));
        assertTrue(data.contains("用户可通过邮箱与密码登录"));
        // 需求描述块进入语义向量化与理由生成的公共输入
        assertTrue(data.contains("【用例标题清单】"));
    }

    @Test
    void multiRequirementBlocks_semanticSearchPerBlock() {
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();
        List<String> blocks = List.of("【需求条目】登录需求\n...\n", "【需求条目】支付需求\n...\n");
        when(requirementContextAssembler.assemble(eq(PROJECT_ID), any(), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "【需求条目】登录需求\n...\n【需求条目】支付需求\n...\n", List.of(), blocks));
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        // 逐块独立检索（4.5：多需求条目各自召回，避免合并单向量稀释偏科）
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), eq(blocks), eq(50), eq(0.7)))
                .thenReturn(List.of(new CaseDedupHit(nodeA, 0.85), new CaseDedupHit(nodeB, 0.8)));
        when(testCaseNodeMapper.selectByIds(List.of(nodeA, nodeB)))
                .thenReturn(List.of(caseNode(nodeA, "验证码登录成功"), caseNode(nodeB, "支付下单成功")));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        stubReasonOut(2);

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(null, List.of(UUID.randomUUID(), UUID.randomUUID()), null));

        assertEquals(2, resp.getItems().size());
        assertEquals(nodeA, resp.getItems().get(0).getCaseNodeId());
        assertEquals(nodeB, resp.getItems().get(1).getCaseNodeId());
        verify(vectorSearchService).searchSimilarCasesByQueries(eq(PROJECT_ID), eq(blocks), eq(50), eq(0.7));
    }

    @Test
    void multiRequirementBlocks_degradedExtractsPerBlock() {
        UUID nodeA = UUID.randomUUID();
        UUID nodeB = UUID.randomUUID();
        String blockA = "【需求条目】登录需求\n...\n";
        String blockB = "【需求条目】支付需求\n...\n";
        when(requirementContextAssembler.assemble(eq(PROJECT_ID), any(), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "【需求条目】登录需求\n...\n【需求条目】支付需求\n...\n", List.of(), List.of(blockA, blockB)));
        when(aiConfigService.getStatus()).thenReturn(status("unavailable"));
        // 逐块分别抽取关键词（每块 ≤10），保证每个需求条目有独立召回词（4.5）
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), eq(blockA)))
                .thenReturn(List.of("验证码"));
        when(aiKeywordExtractor.extract(any(), any(), any(), any(), any(), eq(blockB)))
                .thenReturn(List.of("支付"));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "验证码", 30))
                .thenReturn(List.of(caseNode(nodeA, "验证码登录成功")));
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "支付", 30))
                .thenReturn(List.of(caseNode(nodeB, "支付下单成功")));
        stubReasonOut(2);

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req(null, List.of(UUID.randomUUID(), UUID.randomUUID()), null));

        assertTrue(resp.isSemanticDegraded());
        assertEquals(2, resp.getItems().size());
        assertEquals(nodeA, resp.getItems().get(0).getCaseNodeId());
        assertEquals(nodeB, resp.getItems().get(1).getCaseNodeId());
        // 两个检索块均参与关键词抽取
        verify(aiKeywordExtractor).extract(any(), any(), any(), any(), any(), eq(blockA));
        verify(aiKeywordExtractor).extract(any(), any(), any(), any(), any(), eq(blockB));
    }

    @Test
    void candidatesOverLimit_truncatesTo50() {
        List<CaseDedupHit> hits = new ArrayList<>();
        List<TestCaseNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 55; i++) {
            UUID nodeId = UUID.randomUUID();
            hits.add(new CaseDedupHit(nodeId, 1.0 - i * 0.001));
            nodes.add(caseNode(nodeId, "用例" + i));
        }
        when(aiConfigService.getStatus()).thenReturn(status(Constants.AiSemanticSearch.AVAILABLE));
        when(aiConfigService.getIntSetting("planRecommend.topK")).thenReturn(50);
        when(aiConfigService.getNumberSetting("planRecommend.similarityThreshold")).thenReturn(0.7);
        when(vectorSearchService.searchSimilarCasesByQueries(eq(PROJECT_ID), anyList(), eq(50), eq(0.7)))
                .thenReturn(hits);
        when(testCaseNodeMapper.selectByIds(anyCollection())).thenReturn(nodes);
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));

        AiCasePlanRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("支持手机号验证码登录", null, null));

        // 截断 50 条（3.5）
        assertEquals(50, resp.getItems().size());
    }
}
