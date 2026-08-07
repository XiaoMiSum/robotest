package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiChatModelSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiChatModelRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;

import java.util.List;
import java.util.UUID;

/**
 * 对话模型多行配置管理（增删改查/设默认/启停，详细设计 3.3.7）与运行期模型解析（4.11）。
 */
public interface AiChatModelService {

    /**
     * 全部对话模型（脱敏），默认在前
     */
    List<AiChatModelRespDTO> list();

    /**
     * 新建对话模型（name 全局唯一；首个创建的模型自动置默认）
     */
    AiChatModelRespDTO create(AiChatModelSaveReqDTO reqDTO, UUID operatorId);

    /**
     * 更新对话模型（apiKey 非空即更新、缺省保持原值；updated_at 乐观并发）
     */
    AiChatModelRespDTO update(UUID id, AiChatModelSaveReqDTO reqDTO, UUID operatorId);

    /**
     * 逻辑删除；默认模型不可删除（需先转移默认）
     */
    void delete(UUID id, UUID operatorId);

    /**
     * 设为系统默认（事务内清除原默认再置新默认，唯一默认保证；停用模型不可设默认）
     */
    void setDefault(UUID id, UUID operatorId);

    /**
     * 启用/停用；默认模型不可停用（需先转移默认）
     */
    void setEnabled(UUID id, boolean enabled, UUID operatorId);

    /**
     * 运行期解析（网关对话调用入口）：modelId 命中已启用模型则用之，缺省/失效静默回退系统默认；
     * 密钥缺失或无可用模型返回 null（由调用方按 AI 未启用处理）。不校验总开关（由网关另行门控）。
     */
    ResolvedChatModel resolve(UUID modelId);

    /**
     * 连通性测试解析：临时配置优先，缺省取 modelId 已保存模型（密钥缺省回退该模型已存密文）
     */
    ResolvedChatModel resolveForTest(UUID modelId, AiConfigTestReqDTO.ChatGroup override);

    /**
     * 已启用模型的脱敏视图（id/name/isDefault），供 status 接口下发
     */
    List<AiStatusRespDTO.ChatModelView> listEnabledForStatus();

    /**
     * 是否存在至少一个已启用模型（保存 AI 配置 enabled=true 前置校验与状态计算）
     */
    boolean hasEnabledModel();
}
