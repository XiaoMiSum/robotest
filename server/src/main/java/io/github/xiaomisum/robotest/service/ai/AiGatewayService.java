package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.service.ai.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * AI 调用总入口：限流检查 → Prompt 组装 → Provider 调用 → 输出校验 → 审计
 */
public interface AiGatewayService {

    /**
     * 同步对话调用，返回剥离 think/围栏后的文本内容
     */
    ChatResult complete(AiCallContext context, AiFunctionType functionType,
                        String taskInstruction, String businessData, ChatCallOptions options);

    /**
     * 同步结构化调用：response_format 请求 + Schema 校验，失败追加校验错误重试 1 次，仍失败按 6003
     *
     * @param extraAssertion 业务自定义结构断言（树深度、枚举合法性等），可空
     */
    <T> T completeStructured(AiCallContext context, AiFunctionType functionType,
                             String taskInstruction, String businessData, ChatCallOptions options,
                             Class<T> resultType, Consumer<T> extraAssertion);

    /**
     * SSE 流式调用（统一帧格式：delta/done/error + 15 秒 ping 心跳，总超时 120s，断开取消上游）
     *
     * @param prelude       连接建立后、LLM 调用前执行（业务扩展事件如评审摘要 statistics 帧），可空
     * @param doneAssembler 完整输出 → done 帧载荷（内部做结构化校验，抛 OutputValidationException 按 6003）
     */
    SseEmitter stream(AiCallContext context, AiFunctionType functionType,
                      String taskInstruction, String businessData, ChatCallOptions options,
                      Consumer<SseEmitter> prelude, Function<String, Object> doneAssembler);

    /**
     * Embedding 调用（Embedding 组未配置按 6001）
     */
    List<float[]> embed(AiCallContext context, AiFunctionType functionType, List<String> inputs);
}
