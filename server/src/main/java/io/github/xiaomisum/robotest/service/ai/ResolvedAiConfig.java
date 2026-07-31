package io.github.xiaomisum.robotest.service.ai;

import java.util.Map;

/**
 * 解密后的运行期 Embedding 配置（明文密钥仅存在于调用栈内存，禁止进入日志与响应）。
 *
 * <p>对话模型运行期配置独立为 {@link ResolvedChatModel}（多对话模型，按 modelId 解析）。</p>
 */
public record ResolvedAiConfig(
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
