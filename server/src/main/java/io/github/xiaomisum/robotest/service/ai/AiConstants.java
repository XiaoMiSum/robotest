package io.github.xiaomisum.robotest.service.ai;

/**
 * AI 服务共享常量：跨服务/Handler 同口径的预算与超时收敛于此，避免各自维护导致漂移。
 * 各业务自有的特殊值（如同步调用 5s 短超时）仍保留在对应类内。
 */
public final class AiConstants {

    private AiConstants() {
    }

    /** LLM 调用读超时（ms）：候选集大/输出较长的调用，网关同步默认 15s 不足，功能级覆盖为 60s */
    public static final int LLM_TIMEOUT_MILLIS = 60_000;

    /** 单关键词候选上限（4.3/4.5 同口径：每词取前 30 条） */
    public static final int CANDIDATE_LIMIT_PER_KEYWORD = 30;
}
