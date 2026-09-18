package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;

import java.util.List;
import java.util.UUID;

/**
 * 对话模型回退/降级链（06 §3.1.2）：把 AiChatModelServiceImpl.resolve 内隐式的
 * 「命中启用 → 缺省/失效回退默认 → 无可用/密钥缺失降级 null」显式化为独立裁决单元
 */
public interface ModelFallbackPolicy {

    /**
     * @param modelId   调用方期望的模型 id，可为 null（表示直接使用默认模型）
     * @param enabled   已启用模型快照（不含停用/已删除）
     * @param secretKey 解密密钥，为 null 时（密钥未配置）整链降级 null
     */
    ResolvedChatModel resolve(UUID modelId, List<AiChatModel> enabled, byte[] secretKey);
}