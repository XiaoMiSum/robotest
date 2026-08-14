package io.github.xiaomisum.robotest.service.ai.requirement;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiRequirementSplitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiRequirementSplitRespDTO;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator.OutputValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiRequirementSplitServiceImpl 单元测试（US-AI-019，3.2.3）：
 * 文本截断 + warning、SSE 网关调用、done 帧组装。
 */
@ExtendWith(MockitoExtension.class)
class AiRequirementSplitServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiOutputValidator outputValidator;
    @Mock
    private AiConfigService aiConfigService;

    @Captor
    private ArgumentCaptor<String> businessDataCaptor;
    @Captor
    private ArgumentCaptor<Function<String, Object>> doneAssemblerCaptor;

    @InjectMocks
    private AiRequirementSplitServiceImpl service;

    private AiRequirementSplitReqDTO req(String text) {
        AiRequirementSplitReqDTO dto = new AiRequirementSplitReqDTO();
        dto.setText(text);
        return dto;
    }

    private AiRequirementSplitRespDTO.Module module() {
        AiRequirementSplitRespDTO.Item item = new AiRequirementSplitRespDTO.Item();
        item.setTitle("新增用户");
        item.setContent("管理员可新增用户");
        AiRequirementSplitRespDTO.Module m = new AiRequirementSplitRespDTO.Module();
        m.setModule("用户管理");
        m.setItems(List.of(item));
        return m;
    }

    @Test
    void split_streamsWithTextInBusinessData() {
        when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(20000);
        when(aiGatewayService.stream(any(), eq(AiFunctionType.REQUIREMENT_SPLIT), any(),
                businessDataCaptor.capture(), any(), any(), any())).thenReturn(new SseEmitter());

        service.split(USER_ID, WORKSPACE_ID, PROJECT_ID, req("用户可以通过邮箱登录系统"));

        String businessData = businessDataCaptor.getValue();
        assertTrue(businessData.contains("用户可以通过邮箱登录系统"));
    }

    @Test
    void split_overLimitText_truncatesAndWarns() {
        when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(100);
        when(aiGatewayService.stream(any(), eq(AiFunctionType.REQUIREMENT_SPLIT), any(),
                businessDataCaptor.capture(), any(), any(), doneAssemblerCaptor.capture()))
                .thenReturn(new SseEmitter());
        AiRequirementSplitRespDTO.Payload payload = new AiRequirementSplitRespDTO.Payload();
        payload.setModules(List.of(module()));
        when(outputValidator.parseAndValidate(eq("raw"), eq(AiRequirementSplitRespDTO.Payload.class), any()))
                .thenReturn(payload);

        service.split(USER_ID, WORKSPACE_ID, PROJECT_ID, req("字".repeat(150)));

        String businessData = businessDataCaptor.getValue();
        // 截断后业务数据不含原文末尾
        assertTrue(businessData.contains("【待拆分文档】"));
        assertEquals(100, businessData.length() - "【待拆分文档】\n\n".length());
        // 截断 warning 随 done 帧返回
        Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");
        assertTrue(((List<?>) map.get("warnings")).stream()
                .anyMatch(w -> String.valueOf(w).contains("超出输入预算")));
    }

    @Test
    void doneAssembler_wrapsModulesAndWarnings() {
        when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(20000);
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
        AiRequirementSplitRespDTO.Payload payload = new AiRequirementSplitRespDTO.Payload();
        payload.setModules(List.of(module()));
        when(outputValidator.parseAndValidate(eq("raw"), eq(AiRequirementSplitRespDTO.Payload.class), any()))
                .thenReturn(payload);

        service.split(USER_ID, WORKSPACE_ID, PROJECT_ID, req("需求文档"));

        Map<?, ?> map = (Map<?, ?>) doneAssemblerCaptor.getValue().apply("raw");
        assertEquals(payload.getModules(), map.get("modules"));
        assertTrue(((List<?>) map.get("warnings")).isEmpty());
    }

    @Test
    void doneAssembler_emptyModules_throwsValidation() {
        when(aiConfigService.getIntSetting("importTextMaxLength")).thenReturn(20000);
        when(aiGatewayService.stream(any(), any(), any(), any(), any(), any(),
                doneAssemblerCaptor.capture())).thenReturn(new SseEmitter());
        AiRequirementSplitRespDTO.Payload payload = new AiRequirementSplitRespDTO.Payload();
        payload.setModules(List.of());
        when(outputValidator.parseAndValidate(eq("raw"), eq(AiRequirementSplitRespDTO.Payload.class), any()))
                .thenReturn(payload);

        service.split(USER_ID, WORKSPACE_ID, PROJECT_ID, req("无法识别的文本"));
        // 空 modules 触发结构断言失败 → 网关兜底按 6003 error 帧处理（此处验证抛错）
        assertThrows(OutputValidationException.class,
                () -> doneAssemblerCaptor.getValue().apply("raw"));
    }
}
