package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPriorityRecommendRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优先级推荐服务单测（详细设计 4.3）：规则命中不经网关不计限流；未命中走 LLM 兜底，
 * 失败返回 null（非侵入）。
 */
@ExtendWith(MockitoExtension.class)
class AiPriorityRecommendServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private PriorityRuleEngine priorityRuleEngine;

    @Captor
    private ArgumentCaptor<ChatCallOptions> optionsCaptor;
    @Captor
    private ArgumentCaptor<String> businessDataCaptor;

    @InjectMocks
    private AiPriorityRecommendServiceImpl service;

    private AiPriorityRecommendReqDTO req(String title) {
        AiPriorityRecommendReqDTO dto = new AiPriorityRecommendReqDTO();
        dto.setTitle(title);
        dto.setAncestorTitles(List.of("订单模块", "支付流程"));
        return dto;
    }

    @Test
    void ruleHit_returnsRulePriority_withoutGatewayCall() {
        when(priorityRuleEngine.match("支付失败重试")).thenReturn("P0");

        AiPriorityRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req("支付失败重试"));

        assertEquals("P0", resp.getPriority());
        assertEquals("rule", resp.getSource());
        // 规则命中不经网关（不计限流，4.3）
        verify(aiGatewayService, never()).completeStructured(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ruleMiss_llmHit_returnsLlmPriorityWith5sTimeout() {
        when(priorityRuleEngine.match("常规流程校验")).thenReturn(null);
        AiPriorityRecommendServiceImpl.PriorityOut out = new AiPriorityRecommendServiceImpl.PriorityOut();
        out.setPriority("P2");
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.PRIORITY_RECOMMENDATION), any(),
                businessDataCaptor.capture(), optionsCaptor.capture(),
                eq(AiPriorityRecommendServiceImpl.PriorityOut.class), any())).thenReturn(out);

        AiPriorityRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req("常规流程校验"));

        assertEquals("P2", resp.getPriority());
        assertEquals("llm", resp.getSource());
        // 功能级覆盖读超时 5s（推荐时效价值高于成功率，3.3.1）
        assertEquals(5000, optionsCaptor.getValue().readTimeoutMillis());
        assertEquals(true, optionsCaptor.getValue().jsonResponseFormat());
        // LLM 输入携带标题与模块路径
        assertEquals("【用例标题】常规流程校验\n【所属模块路径】订单模块 > 支付流程\n",
                businessDataCaptor.getValue());
    }

    @Test
    void ruleMiss_llmFails_returnsNullPriority() {
        when(priorityRuleEngine.match("常规流程校验")).thenReturn(null);
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.PRIORITY_RECOMMENDATION), any(), any(), any(),
                eq(AiPriorityRecommendServiceImpl.PriorityOut.class), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_CALL_FAILED));

        AiPriorityRecommendRespDTO resp = service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req("常规流程校验"));

        // 非侵入原则：失败静默返回空推荐，不向用户提示错误
        assertNull(resp.getPriority());
        assertEquals("llm", resp.getSource());
    }

    @Test
    void ruleMiss_ancestorTitlesAbsent_omitsModuleSection() {
        when(priorityRuleEngine.match("常规流程校验")).thenReturn(null);
        AiPriorityRecommendServiceImpl.PriorityOut out = new AiPriorityRecommendServiceImpl.PriorityOut();
        out.setPriority("P1");
        when(aiGatewayService.completeStructured(
                any(), eq(AiFunctionType.PRIORITY_RECOMMENDATION), any(),
                businessDataCaptor.capture(), any(), any(), any())).thenReturn(out);

        AiPriorityRecommendReqDTO req = new AiPriorityRecommendReqDTO();
        req.setTitle("常规流程校验");
        service.recommend(USER_ID, WORKSPACE_ID, PROJECT_ID, req);

        assertEquals("【用例标题】常规流程校验\n", businessDataCaptor.getValue());
    }
}
