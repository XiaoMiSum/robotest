package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiKeywordExtractRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.project.RequirementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 遗漏测试点分析服务单测（详细设计 3.3 / 4.3 关键词版）：
 * 三态输入校验、saveAsRequirement 不阻断、LLM 抽取关键词、候选检索、结构断言与幻觉过滤。
 */
@ExtendWith(MockitoExtension.class)
class AiMissingPointServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DIR_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private RequirementService requirementService;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<ChatCallOptions> optionsCaptor;
    @Captor
    private ArgumentCaptor<Consumer<AiMissingPointServiceImpl.MissingPointOut>> assertionCaptor;

    @InjectMocks
    private AiMissingPointServiceImpl service;

    private AiMissingPointReqDTO req(String keyword) {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setKeywords(keyword == null ? null : List.of(keyword));
        return dto;
    }

    private AiMissingPointReqDTO reqWithText(String text) {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setText(text);
        return dto;
    }

    private TestCaseModule document() {
        TestCaseModule module = new TestCaseModule();
        module.setId(DOC_ID);
        module.setProjectId(PROJECT_ID);
        module.setParentId(DIR_ID);
        module.setType(Constants.ModuleType.DOCUMENT);
        module.setName("验证码登录");
        return module;
    }

    private TestCaseModule directory() {
        TestCaseModule module = new TestCaseModule();
        module.setId(DIR_ID);
        module.setProjectId(PROJECT_ID);
        module.setParentId(null);
        module.setType(Constants.ModuleType.DIRECTORY);
        module.setName("登录模块");
        return module;
    }

    private TestCaseNode caseNode(UUID id, String title) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setDocumentId(DOC_ID);
        node.setType(Constants.NodeType.CASE);
        node.setTitle(title);
        return node;
    }

    private RequirementPoolItem requirementItem(UUID id, String title, String content) {
        RequirementPoolItem item = new RequirementPoolItem();
        item.setId(id);
        item.setProjectId(PROJECT_ID);
        item.setTitle(title);
        item.setContent(content);
        return item;
    }

    private AiMissingPointServiceImpl.MissingPointOut out(String title, String path, List<String> related) {
        AiMissingPointServiceImpl.MissingPointOut.Point point =
                new AiMissingPointServiceImpl.MissingPointOut.Point();
        point.setTitle(title);
        point.setDescription("需求提及该场景，现有用例未覆盖");
        point.setSuggestedModulePath(path);
        point.setRelatedCaseTitles(related);
        AiMissingPointServiceImpl.MissingPointOut out =
                new AiMissingPointServiceImpl.MissingPointOut();
        out.setPoints(List.of(point));
        return out;
    }

    private void stubProjectModules() {
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory(), document()));
    }

    @Test
    void allInputsEmpty_throws() {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        assertThrows(ServiceException.class,
                () -> service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void saveAsRequirementWithoutText_throws() {
        AiMissingPointReqDTO dto = req("登录");
        AiMissingPointReqDTO.SaveAsRequirement saveAs = new AiMissingPointReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);
        dto.setKeywords(null);
        assertThrows(ServiceException.class,
                () -> service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void saveAsRequirement_savesItemBeforeAnalysis() {
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));
        AiMissingPointReqDTO dto = req("登录");
        dto.setText("需求：支持手机号验证码登录");
        AiMissingPointReqDTO.SaveAsRequirement saveAs = new AiMissingPointReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        verify(requirementService).create(eq(PROJECT_ID), eq(USER_ID), any(RequirementCreateReqDTO.class));
    }

    @Test
    void saveAsRequirementSaveFails_continuesAnalysis() {
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));
        AiMissingPointReqDTO dto = req("登录");
        dto.setText("需求：支持手机号验证码登录");
        AiMissingPointReqDTO.SaveAsRequirement saveAs = new AiMissingPointReqDTO.SaveAsRequirement();
        saveAs.setTitle("登录需求");
        dto.setSaveAsRequirement(saveAs);
        doThrow(new ServiceException()).when(requirementService)
                .create(eq(PROJECT_ID), eq(USER_ID), any(RequirementCreateReqDTO.class));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        // 保存失败不阻断分析（3.3）
        assertEquals(1, resp.getPoints().size());
    }

    @Test
    void keywordsProvided_skipsExtraction_usesKeywordRetrieval() {
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), optionsCaptor.capture(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录"));

        assertTrue(resp.isSemanticDegraded());
        assertEquals("短信验证码超时后重新发送", resp.getPoints().get(0).getTitle());
        // 比对读超时功能级覆盖 60s（4.3）
        assertEquals(60_000, optionsCaptor.getValue().readTimeoutMillis());
        // 有入参关键词时不触发 LLM 抽取
        verify(aiGatewayService, never()).completeStructured(any(), eq(AiFunctionType.KEYWORD_EXTRACTION),
                any(), any(), any(), any(), any());
        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【需求关键词】登录"));
        assertTrue(data.contains("验证码登录成功｜模块：登录模块/验证码登录"));
    }

    @Test
    void textOnly_extractsKeywordsOnceThenAnalyzes() {
        stubProjectModules();
        AiKeywordExtractRespDTO extract = new AiKeywordExtractRespDTO();
        extract.setKeywords(List.of("验证码"));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.KEYWORD_EXTRACTION), any(),
                any(), any(), eq(AiKeywordExtractRespDTO.class), any())).thenReturn(extract);
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "验证码", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID,
                reqWithText("需求：验证码有效期 5 分钟，过期可重新发送"));

        assertEquals(1, resp.getPoints().size());
        // 抽取一次同步调用 + 比对一次
        verify(aiGatewayService, times(1)).completeStructured(any(), eq(AiFunctionType.KEYWORD_EXTRACTION),
                any(), any(), any(), any(), any());
        verify(aiGatewayService, times(1)).completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS),
                any(), any(), any(), any(), any());
        // 抽取结果作为检索关键词
        verify(testCaseNodeMapper).listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "验证码", 30);
    }

    @Test
    void hallucinatedRelatedTitles_filteredOut() {
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录",
                        List.of("验证码登录成功", "不存在的用例标题")));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录"));

        assertEquals(List.of("验证码登录成功"), resp.getPoints().get(0).getRelatedCaseTitles());
    }

    @Test
    void suggestedModulePathNotInCandidates_assertionRejects() {
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), assertionCaptor.capture()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录"));

        // 直接驱动结构断言：非法模块路径应抛校验异常（网关据此带错重试，服务测试只验证断言行为）
        AiMissingPointServiceImpl.MissingPointOut invalid =
                out("越权访问校验", "不存在的模块/子模块", List.of());
        assertThrows(AiOutputValidator.OutputValidationException.class,
                () -> assertionCaptor.getValue().accept(invalid));
        // 合法模块路径放行
        assertionCaptor.getValue().accept(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of()));
    }

    @Test
    void requirementItems_appendedToBlock() {
        UUID reqId = UUID.randomUUID();
        when(requirementService.requireByIds(PROJECT_ID, List.of(reqId)))
                .thenReturn(List.of(requirementItem(reqId, "登录需求", "用户可通过邮箱与密码登录")));
        stubProjectModules();
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of()));

        AiMissingPointReqDTO dto = req("登录");
        dto.setRequirementIds(List.of(reqId));
        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【需求条目】登录需求"));
        assertTrue(data.contains("用户可通过邮箱与密码登录"));
    }

    @Test
    void candidateOverBudget_truncatesTrailingCandidates() {
        when(testCaseModuleMapper.findDocumentModulesByProjectId(PROJECT_ID))
                .thenReturn(List.of(document()));
        when(testCaseModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(document()));
        List<TestCaseNode> nodes = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            nodes.add(caseNode(UUID.randomUUID(), i + "甲".repeat(280)));
        }
        when(testCaseNodeMapper.listCaseNodesByDocumentIdsAndKeyword(List.of(DOC_ID), "登录", 30))
                .thenReturn(nodes);
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "验证码登录", List.of()));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录"));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("1" + "甲".repeat(280)));
        // 候选清单超出预算时静默截断尾部，避免整体输入预算失守
        assertFalse(data.contains("30" + "甲".repeat(280)));
    }
}
