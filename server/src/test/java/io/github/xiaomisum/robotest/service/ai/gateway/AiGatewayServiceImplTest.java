package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.StreamCallbacks;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedAiConfig;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiSseSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiGatewayServiceImpl 单测 —— 重点覆盖 stream 路径的结构化带错重试（设计 4.4）：
 * 首次输出校验失败时追加错误说明同步重试一次，成功交付 done 帧且 token 按两轮累计，
 * 双败才记 schema_invalid/6003；首次即通过时不触发重试。
 */
@ExtendWith(MockitoExtension.class)
class AiGatewayServiceImplTest {

    private static final String MODEL_NAME = "oc/deepseek-v4-flash-free";

    @Mock
    private AiSseSupport sseSupport;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private AiChatModelService aiChatModelService;
    @Mock
    private OpenAiCompatProvider provider;
    @Mock
    private PromptAssembler promptAssembler;
    @Mock
    private AiRateLimiter rateLimiter;
    @Mock
    private AiAuditRecorder auditRecorder;
    @Mock
    private AiOutputValidator outputValidator;

    @InjectMocks
    private AiGatewayServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID modelId = UUID.randomUUID();

    @BeforeEach
    void stubSseSupport() {
        // 虚拟线程内正常路径会 stopPing()，ping 句柄需为可取消 mock 而非 null
        lenient().when(sseSupport.open()).thenReturn(new AiSseSupport.Channel(
                new SseEmitter(), new AtomicBoolean(false), mock(ScheduledFuture.class)));
    }

    private AiCallContext context() {
        return new AiCallContext(userId, workspaceId, projectId, modelId);
    }

    private ResolvedChatModel chatModel() {
        return new ResolvedChatModel(modelId, "本地 DeepSeek", "custom",
                "http://localhost:20128/v1", "key", MODEL_NAME, Map.of());
    }

    private void stubThreadReachable() {
        when(aiConfigService.getResolvedConfig()).thenReturn(mock(ResolvedAiConfig.class));
        when(aiChatModelService.resolve(modelId)).thenReturn(chatModel());
        when(promptAssembler.assemble(any(), any(), any())).thenReturn(List.of());
    }

    /** 模拟上游流式：收到完整内容后回调 onFinish（token 取首次调用用量） */
    private void stubStreamFinish(String content, Integer promptTokens, Integer completionTokens) {
        doAnswer(inv -> {
            StreamCallbacks cb = inv.getArgument(3);
            cb.onFinish(content, promptTokens, completionTokens);
            return null;
        }).when(provider).stream(any(), anyList(), any(), any(), any());
    }

    /**
     * 模拟带错重试的两轮流式：首次 onFinish 回调 badContent，重试（仍为流式，第二次调用）
     * onFinish 回调 goodContent；两轮 token 分别记账。
     */
    private void stubStreamWithRetry(String badContent, Integer firstPrompt, Integer firstCompletion,
                                     String goodContent, Integer retryPrompt, Integer retryCompletion) {
        AtomicInteger callIndex = new AtomicInteger();
        doAnswer(inv -> {
            StreamCallbacks cb = inv.getArgument(3);
            if (callIndex.getAndIncrement() == 0) {
                cb.onFinish(badContent, firstPrompt, firstCompletion);
            } else {
                cb.onFinish(goodContent, retryPrompt, retryCompletion);
            }
            return null;
        }).when(provider).stream(any(), anyList(), any(), any(), any());
    }

    /** done 组装器：仅当内容含 "good" 时视为通过校验，否则抛结构化校验失败 */
    private static Function<String, Object> strictAssembler() {
        return content -> {
            if (!content.contains("good")) {
                throw new AiOutputValidator.OutputValidationException("输出中未找到完整的 JSON 结构");
            }
            return Map.of("nodes", List.of(), "warnings", List.of());
        };
    }

    @Test
    void stream_firstAttemptInvalid_retriesOnceAndSucceeds() {
        stubThreadReachable();
        // 首次流式输出不合规 → 触发带错重试；重试仍为流式（第二次 stream 调用），输出合规
        stubStreamWithRetry("bad json", 100, 200, "good json", 300, 400);

        service.stream(context(), AiFunctionType.CASE_GENERATION, "生成测试用例子树",
                "业务数据", ChatCallOptions.json(), null, strictAssembler());

        // 重试成功：审计按两轮 token 累加（100+300 / 200+400），成功态
        verify(auditRecorder, timeout(3000)).record(any(), eq(AiFunctionType.CASE_GENERATION),
                eq(MODEL_NAME), anyLong(), eq(400), eq(600),
                eq(Constants.AiInvocationStatus.SUCCESS), isNull());
        // done 帧交付重试结果，不发送 error 帧
        verify(sseSupport, timeout(3000)).send(any(), eq("done"), any());
        verify(sseSupport, never()).sendError(any(), any(), any());
        // 重试指令追加了校验错误说明（4.4）：第二次 assemble 携带错误信息
        ArgumentCaptor<String> instructionCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptAssembler, timeout(3000).times(2))
                .assemble(eq(AiFunctionType.CASE_GENERATION), instructionCaptor.capture(), any());
        String retryInstruction = instructionCaptor.getAllValues().get(1);
        assertTrue(retryInstruction.contains("上一次输出未通过校验"));
        assertTrue(retryInstruction.contains("输出中未找到完整的 JSON 结构"));
    }

    @Test
    void stream_bothAttemptsInvalid_recordsSchemaInvalid() {
        stubThreadReachable();
        // 首次流式与重试（仍为流式）输出均不合规 → 双败记 6003
        stubStreamWithRetry("bad json", 100, 200, "still bad", 300, 400);
        Function<String, Object> alwaysFailing = content -> {
            throw new AiOutputValidator.OutputValidationException("输出中未找到完整的 JSON 结构");
        };

        service.stream(context(), AiFunctionType.CASE_GENERATION, "生成测试用例子树",
                "业务数据", ChatCallOptions.json(), null, alwaysFailing);

        // 双败：审计 schema_invalid/6003，token 两轮累计；error 帧送达，不发 done 帧
        verify(auditRecorder, timeout(3000)).record(any(), eq(AiFunctionType.CASE_GENERATION),
                eq(MODEL_NAME), anyLong(), eq(400), eq(600),
                eq(Constants.AiInvocationStatus.SCHEMA_INVALID), eq("6003"));
        verify(sseSupport, timeout(3000)).sendError(any(), eq(1000013003), eq("AI 输出结构化校验失败"));
        verify(sseSupport, never()).send(any(), eq("done"), any());
    }

    @Test
    void stream_firstAttemptValid_noRetry() {
        stubThreadReachable();
        // 首次输出即通过校验：不触发重试，token 仅为首次用量
        stubStreamFinish("good json", 10, 5);

        service.stream(context(), AiFunctionType.CASE_GENERATION, "生成测试用例子树",
                "业务数据", ChatCallOptions.json(), null, strictAssembler());

        verify(auditRecorder, timeout(3000)).record(any(), eq(AiFunctionType.CASE_GENERATION),
                eq(MODEL_NAME), anyLong(), eq(10), eq(5),
                eq(Constants.AiInvocationStatus.SUCCESS), isNull());
        verify(provider, never()).complete(any(), anyList(), any());
        verify(sseSupport, timeout(3000)).send(any(), eq("done"), any());
        // 首次即通过：assemble 仅前置组装一次，不触发带错重试的第二次组装
        verify(promptAssembler, timeout(3000).times(1))
                .assemble(eq(AiFunctionType.CASE_GENERATION), any(), any());
    }
}
