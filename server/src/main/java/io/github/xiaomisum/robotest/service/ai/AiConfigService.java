package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiConnectivityTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;

import java.util.Map;

public interface AiConfigService {

    /**
     * 获取 AI 配置（脱敏），未配置返回 null
     */
    AiConfigRespDTO getConfig();

    /**
     * 保存 AI 配置（updated_at 乐观并发；Embedding 模型/维度变更触发向量重建联动）
     */
    AiConfigRespDTO saveConfig(AiConfigSaveReqDTO reqDTO);

    /**
     * 连通性测试：chat 最小对话 + 结构化参数探测 / embedding 维度核验；失败以业务结果返回
     */
    AiConnectivityTestRespDTO testConnectivity(AiConfigTestReqDTO reqDTO);

    /**
     * AI 可用性与语义检索降级状态（30 秒缓存）
     */
    AiStatusRespDTO getStatus();

    /**
     * settings 键值集（内置默认值与落库覆盖合并后的完整视图）
     */
    Map<String, Object> getMergedSettings();

    int getIntSetting(String key);

    double getNumberSetting(String key);

    /**
     * 解密后的运行期配置；AI 未启用（无配置/开关关闭/密钥缺失或解密失败）返回 null
     */
    ResolvedAiConfig getResolvedConfig();
}
