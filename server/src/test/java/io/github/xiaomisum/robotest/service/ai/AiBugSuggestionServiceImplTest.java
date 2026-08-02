package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugSuggestionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugSuggestionRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷表单智能建议单测（详细设计 3.1/4.5）：同步 LLM 调用映射响应；上下文仅标题+重现步骤（超长截断不报错）；
 * 等级/优先级合法性经 extraAssertion 二次校验兜底。
 */
@ExtendWith(MockitoExtension.class)
class AiBugSuggestionServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;

    @InjectMocks
    private AiBugSuggestionServiceImpl service;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<ChatCallOptions> optionsCaptor;
    @Captor
    private ArgumentCaptor<Consumer<AiBugSuggestionServiceImpl.SuggestionOut>> assertionCaptor;

    private AiBugSuggestionReqDTO req(String title, String reproSteps) {
        AiBugSuggestionReqDTO dto = new AiBugSuggestionReqDTO();
        dto.setTitle(title);
        dto.setReproSteps(reproSteps);
        return dto;
    }

    private AiBugSuggestionServiceImpl.SuggestionOut out(String optimizedTitle, String severity,
            String priority, String reason) {
        AiBugSuggestionServiceImpl.SuggestionOut out = new AiBugSuggestionServiceImpl.SuggestionOut();
        out.setOptimizedTitle(optimizedTitle);
        out.setSeverity(severity);
        out.setPriority(priority);
        out.setReason(reason);
        return out;
    }

    @Test
    void suggest_success_mapsAllFields() {
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.BUG_FORM_SUGGESTION), any(),
                businessDataCaptor.capture(), optionsCaptor.capture(),
                eq(AiBugSuggestionServiceImpl.SuggestionOut.class), any()))
                .thenReturn(out("登录按钮无响应", "serious", "high", "阻断核心登录流程"));

        AiBugSuggestionRespDTO resp = service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID,
                req("点登录没反应", "点登录无任何提示"));

        assertEquals("登录按钮无响应", resp.getOptimizedTitle());
        assertEquals("serious", resp.getSeverity());
        assertEquals("high", resp.getPriority());
        assertEquals("阻断核心登录流程", resp.getReason());
        // 结构化 JSON 调用（3.1 同步表单建议）
        assertEquals(true, optionsCaptor.getValue().jsonResponseFormat());
    }

    @Test
    void suggest_buildsContextWithTitleAndReproSteps() {
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.BUG_FORM_SUGGESTION), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("x", "general", "medium", "r"));

        service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时", "填写账号后点登录，30 秒无响应"));

        String data = businessDataCaptor.getValue();
        assertTrue(data.contains("【缺陷标题】登录超时"));
        assertTrue(data.contains("【重现步骤】\n填写账号后点登录，30 秒无响应"));
    }

    @Test
    void suggest_reproStepsNull_omitsSection() {
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.BUG_FORM_SUGGESTION), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("x", "general", "low", "r"));

        service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时", null));

        assertFalse(businessDataCaptor.getValue().contains("重现步骤"));
    }

    @Test
    void suggest_reproStepsOverBudget_truncatedNotRejected() {
        String longRepro = "字".repeat(AiBugSuggestionServiceImpl.INPUT_TOKEN_BUDGET * 2);
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.BUG_FORM_SUGGESTION), any(),
                businessDataCaptor.capture(), any(), any(), any()))
                .thenReturn(out("x", "general", "medium", "r"));

        service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时", longRepro));

        String data = businessDataCaptor.getValue();
        // 超长截断不报错（3.1/4.5），不送入全文
        assertFalse(data.contains(longRepro));
        assertTrue(data.length() < longRepro.length() + 20);
    }

    @Test
    void assertion_invalidSeverity_rejected() {
        AiBugSuggestionServiceImpl.SuggestionOut valid = out("x", "general", "low", "r");
        when(aiGatewayService.completeStructured(
                any(), any(), any(), any(), any(), any(), assertionCaptor.capture()))
                .thenReturn(valid);

        service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时", null));

        Consumer<AiBugSuggestionServiceImpl.SuggestionOut> assertion = assertionCaptor.getValue();
        // 枚举合法性兜底（3.1 响应校验：severity ∈ fatal/serious/general/minor）
        assertThrows(AiOutputValidator.OutputValidationException.class,
                () -> assertion.accept(out("x", "critical", "low", "r")));
        // 合法值不抛
        assertion.accept(valid);
    }

    @Test
    void assertion_invalidPriority_rejected() {
        when(aiGatewayService.completeStructured(
                any(), any(), any(), any(), any(), any(), assertionCaptor.capture()))
                .thenReturn(out("x", "general", "low", "r"));

        service.suggest(USER_ID, WORKSPACE_ID, PROJECT_ID, req("登录超时", null));

        Consumer<AiBugSuggestionServiceImpl.SuggestionOut> assertion = assertionCaptor.getValue();
        assertThrows(AiOutputValidator.OutputValidationException.class,
                () -> assertion.accept(out("x", "general", "urgent", "r")));
    }
}
