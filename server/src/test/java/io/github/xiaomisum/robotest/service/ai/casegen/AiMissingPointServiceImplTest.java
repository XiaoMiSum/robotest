package io.github.xiaomisum.robotest.service.ai.casegen;


import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiMissingPointReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiMissingPointRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiRequirementContextAssembler;
import java.util.function.Consumer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 遗漏测试点分析服务单测（详细设计 3.3 / 4.3，文档级全量候选）：
 * 输入校验、文档级候选全量检索、结构断言与幻觉过滤。
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
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private AiRequirementContextAssembler requirementContextAssembler;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<ChatCallOptions> optionsCaptor;
    @Captor
    private ArgumentCaptor<Consumer<AiMissingPointServiceImpl.MissingPointOut>> assertionCaptor;

    @InjectMocks
    private AiMissingPointServiceImpl service;

    @BeforeEach
    void stubDefaultRequirementContext() {
        // 大部分场景不关心需求上下文内容，空上下文即可；需要特定内容的测试单独覆盖
        lenient().when(requirementContextAssembler.assemble(any(), any(), any(), any()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext("", List.of()));
    }

    /** 基础请求：documentIds 恒为当前文档 + 需求文本（text/requirementIds 至少一项非空） */
    private AiMissingPointReqDTO req(String text) {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setDocumentIds(List.of(DOC_ID));
        dto.setText(text);
        return dto;
    }

    private AiMissingPointReqDTO reqWithRequirementIds(List<UUID> requirementIds) {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setDocumentIds(List.of(DOC_ID));
        dto.setRequirementIds(requirementIds);
        return dto;
    }

    private TestCaseDocument document() {
        TestCaseDocument module = new TestCaseDocument();
        module.setId(DOC_ID);
        module.setProjectId(PROJECT_ID);
        module.setModuleId(DIR_ID);
        module.setName("验证码登录");
        return module;
    }

    private ProjectModule directory() {
        ProjectModule module = new ProjectModule();
        module.setId(DIR_ID);
        module.setProjectId(PROJECT_ID);
        module.setParentId(null);
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

    private void stubDocumentModules() {
        when(testCaseDocumentMapper.selectBatchIds(List.of(DOC_ID))).thenReturn(List.of(document()));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(directory()));
    }

    private void stubSingleCaseNode() {
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(List.of(DOC_ID)))
                .thenReturn(List.of(caseNode(UUID.randomUUID(), "验证码登录成功")));
    }

    @Test
    void allInputsEmpty_throws() {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        assertThrows(ServiceException.class,
                () -> service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void documentIdsMissing_throws() {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setText("需求：验证码有效期 5 分钟");
        assertThrows(ServiceException.class,
                () -> service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void textAndRequirementIdsBothEmpty_throws() {
        AiMissingPointReqDTO dto = new AiMissingPointReqDTO();
        dto.setDocumentIds(List.of(DOC_ID));
        assertThrows(ServiceException.class,
                () -> service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto));
    }

    @Test
    void textOnly_retrievesAllDocumentCasesWithoutExtraction() {
        stubDocumentModules();
        stubSingleCaseNode();
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), optionsCaptor.capture(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("需求：验证码有效期 5 分钟，过期可重新发送"));

        assertEquals(1, resp.getPoints().size());
        assertEquals("短信验证码超时后重新发送", resp.getPoints().get(0).getTitle());
        // 比对读超时功能级覆盖 300s（4.3）
        assertEquals(300_000, optionsCaptor.getValue().readTimeoutMillis());
        // 候选为文档级全量，单次检索、无关键词中间调用
        verify(testCaseNodeMapper).listCaseNodesByDocumentIds(List.of(DOC_ID));
        verify(aiGatewayService, times(1)).completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS),
                any(), any(), any(), any(), any());
        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("验证码登录成功｜模块：登录模块/验证码登录"));
    }

    @Test
    void modelId_forwardedToCallContext() {
        stubDocumentModules();
        stubSingleCaseNode();
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        UUID modelId = UUID.randomUUID();
        AiMissingPointReqDTO dto = req("需求：验证码有效期 5 分钟");
        dto.setModelId(modelId);
        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, dto);

        ArgumentCaptor<AiCallContext> contextCaptor = ArgumentCaptor.forClass(AiCallContext.class);
        verify(aiGatewayService).completeStructured(contextCaptor.capture(),
                eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(), any(), any(), any(), any());
        assertEquals(modelId, contextCaptor.getValue().modelId());
    }

    @Test
    void hallucinatedRelatedTitles_filteredOut() {
        stubDocumentModules();
        stubSingleCaseNode();
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录",
                        List.of("验证码登录成功", "不存在的用例标题")));

        AiMissingPointRespDTO resp = service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("需求：验证码"));

        assertEquals(List.of("验证码登录成功"), resp.getPoints().get(0).getRelatedCaseTitles());
    }

    @Test
    void suggestedModulePathNotInCandidates_assertionRejects() {
        stubDocumentModules();
        stubSingleCaseNode();
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                any(), any(), any(), assertionCaptor.capture()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of("验证码登录成功")));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("需求：验证码"));

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
        when(requirementContextAssembler.assemble(eq(PROJECT_ID), eq(List.of(reqId)), any(), isNull()))
                .thenReturn(new AiRequirementContextAssembler.RequirementContext(
                        "【需求条目】登录需求\n用户可通过邮箱与密码登录\n", List.of()));
        stubDocumentModules();
        stubSingleCaseNode();
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "登录模块/验证码登录", List.of()));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, reqWithRequirementIds(List.of(reqId)));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【需求条目】登录需求"));
        assertTrue(data.contains("用户可通过邮箱与密码登录"));
    }

    @Test
    void candidateOverBudget_truncatesTrailingCandidates() {
        when(testCaseDocumentMapper.selectBatchIds(List.of(DOC_ID))).thenReturn(List.of(document()));
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        List<TestCaseNode> nodes = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            nodes.add(caseNode(UUID.randomUUID(), i + "甲".repeat(280)));
        }
        when(testCaseNodeMapper.listCaseNodesByDocumentIds(List.of(DOC_ID))).thenReturn(nodes);
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "验证码登录", List.of()));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("需求：验证码"));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("1" + "甲".repeat(280)));
        // 候选清单超出预算时静默截断尾部，避免整体输入预算失守
        assertFalse(data.contains("30" + "甲".repeat(280)));
    }

    @Test
    void unknownDocument_returnsEmptyCandidatesWithoutQuery() {
        when(testCaseDocumentMapper.selectBatchIds(List.of(DOC_ID))).thenReturn(List.of());
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.MISSING_POINT_ANALYSIS), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("短信验证码超时后重新发送", "", List.of()));

        service.analyze(USER_ID, WORKSPACE_ID, PROJECT_ID, req("需求：验证码"));

        verify(testCaseNodeMapper, never()).listCaseNodesByDocumentIds(any());
        assertTrue(businessDataCaptor.getValue().contains("【候选用例清单】"));
    }
}
