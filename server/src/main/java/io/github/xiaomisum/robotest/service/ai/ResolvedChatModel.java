package io.github.xiaomisum.robotest.service.ai;

import java.util.Map;
import java.util.UUID;

/**
 * 解密后的运行期对话模型配置（明文密钥仅存在于调用栈内存，禁止进入日志与响应）。
 *
 * <p>由 {@link AiChatModelService#resolve} 按 modelId 解析或回退系统默认模型得到，
 * 供网关对话调用与 OpenAI 兼容 Provider 使用。</p>
 */
public record ResolvedChatModel(
        UUID id,
        String name,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Map<String, Object> extraParams) {
}
