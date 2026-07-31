package io.github.xiaomisum.robotest.service.ai;

import java.util.Map;

/**
 * 解密后的运行期 AI 配置（明文密钥仅存在于调用栈内存，禁止进入日志与响应）
 */
public record ResolvedAiConfig(
        String chatProvider,
        String chatBaseUrl,
        String chatApiKey,
        String chatModel,
        Map<String, Object> chatExtraParams,
        String embeddingProvider,
        String embeddingBaseUrl,
        String embeddingApiKey,
        String embeddingModel,
        Integer embeddingDimension,
        Map<String, Object> embeddingExtraParams) {

    /**
     * Embedding 组是否配置完整（未配置则语义检索能力不可用）
     */
    public boolean embeddingConfigured() {
        return embeddingBaseUrl != null && !embeddingBaseUrl.isBlank()
                && embeddingModel != null && !embeddingModel.isBlank()
                && embeddingApiKey != null && !embeddingApiKey.isBlank()
                && embeddingDimension != null;
    }
}
