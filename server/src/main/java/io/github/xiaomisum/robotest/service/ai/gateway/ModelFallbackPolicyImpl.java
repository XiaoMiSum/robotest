package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import io.github.xiaomisum.robotest.service.ai.support.AiCryptoUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回退链唯一实现：modelId 命中的模型在启用快照内则使用；缺省或失效（不存在/已停用/已删除）静默回退默认模型；
 * 无可用模型或密钥缺失（secretKey 为 null / 解密失败）时整链降级 null，由调用方决定是否拒绝服务。
 */
@Service
public class ModelFallbackPolicyImpl implements ModelFallbackPolicy {

    @Override
    public ResolvedChatModel resolve(UUID modelId, List<AiChatModel> enabled, byte[] secretKey) {
        AiChatModel row = null;
        if (modelId != null) {
            row = enabled.stream().filter(m -> modelId.equals(m.getId())).findFirst().orElse(null);
        }
        if (row == null) {
            // modelId 缺省或失效（不存在/已停用/已删除）静默回退系统默认（4.11）
            row = enabled.stream().filter(m -> Boolean.TRUE.equals(m.getIsDefault())).findFirst().orElse(null);
        }
        if (row == null) {
            return null;
        }
        String apiKey = AiCryptoUtil.decrypt(secretKey, row.getApiKeyCipher());
        if (apiKey == null) {
            return null;
        }
        return new ResolvedChatModel(row.getId(), row.getName(), row.getProvider(), row.getBaseUrl(),
                apiKey, row.getModel(), row.getExtraParams() != null ? row.getExtraParams() : Map.of());
    }
}