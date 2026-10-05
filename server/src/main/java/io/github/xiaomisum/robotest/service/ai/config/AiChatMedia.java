package io.github.xiaomisum.robotest.service.ai.config;

/**
 * 单次调用随行的多模态媒体（详设 3.6.1 requirement_import 图片直传）：
 * mimeType 取图片 MIME（image/png 等），data 为原始字节，由客户端交给 Spring AI 序列化。
 */
public record AiChatMedia(String mimeType, byte[] data) {
}
