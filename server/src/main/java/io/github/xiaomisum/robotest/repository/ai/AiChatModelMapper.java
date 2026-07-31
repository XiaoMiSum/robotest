package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiChatModel;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiChatModelMapper extends BaseMapperX<AiChatModel> {

    /**
     * 全部未删除对话模型（管理端列表：默认在前，其次按创建时间）
     */
    default List<AiChatModel> findAllActive() {
        return selectList(new LambdaQueryWrapperX<AiChatModel>()
                .orderByDesc(AiChatModel::getIsDefault)
                .orderByAsc(AiChatModel::getCreatedAt));
    }

    /**
     * 已启用且未删除的对话模型（运行期解析清单与状态下发数据源）
     */
    default List<AiChatModel> findEnabledActive() {
        return selectList(new LambdaQueryWrapperX<AiChatModel>()
                .eq(AiChatModel::getEnabled, true)
                .orderByDesc(AiChatModel::getIsDefault)
                .orderByAsc(AiChatModel::getCreatedAt));
    }

    /**
     * 系统默认模型（已启用、未删除），无默认返回 null
     */
    default AiChatModel findDefaultActive() {
        return selectOne(new LambdaQueryWrapperX<AiChatModel>()
                .eq(AiChatModel::getIsDefault, true)
                .eq(AiChatModel::getEnabled, true)
                .last("LIMIT 1"));
    }

    /**
     * 按 id 查未删除模型（管理操作定位），不存在返回 null
     */
    default AiChatModel findByIdActive(UUID id) {
        return selectOne(new LambdaQueryWrapperX<AiChatModel>()
                .eq(AiChatModel::getId, id)
                .last("LIMIT 1"));
    }

    /**
     * 按显示名查未删除模型（唯一性校验），不存在返回 null
     */
    default AiChatModel findByNameActive(String name) {
        return selectOne(new LambdaQueryWrapperX<AiChatModel>()
                .eq(AiChatModel::getName, name)
                .last("LIMIT 1"));
    }

    // ========== 条件更新（封装 wrapper 于 Mapper，Service 层不直接构建，4.11） ==========

    /**
     * 更新模型主体字段（updated_at 乐观条件更新，冲突返回 0 行）
     */
    default int updateModelFields(UUID id, LocalDateTime expectedUpdatedAt, String name, String provider,
                                  String baseUrl, String model, String apiKeyCipher, String keySuffix,
                                  String extraParamsJson, UUID operatorId) {
        return update(null, new LambdaUpdateWrapperX<AiChatModel>()
                .eq(AiChatModel::getId, id)
                .eq(AiChatModel::getUpdatedAt, expectedUpdatedAt)
                .set(AiChatModel::getName, name)
                .set(AiChatModel::getProvider, provider)
                .set(AiChatModel::getBaseUrl, baseUrl)
                .set(AiChatModel::getModel, model)
                .set(AiChatModel::getApiKeyCipher, apiKeyCipher)
                .set(AiChatModel::getKeySuffix, keySuffix)
                .set(AiChatModel::getExtraParams, extraParamsJson)
                .set(AiChatModel::getUpdatedBy, operatorId)
                .set(AiChatModel::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 清除当前默认（设默认第一步，全系统唯一默认保证）
     */
    default int clearDefault() {
        return update(null, new LambdaUpdateWrapperX<AiChatModel>()
                .eq(AiChatModel::getIsDefault, true)
                .set(AiChatModel::getIsDefault, false)
                .set(AiChatModel::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 置为默认（仅对已启用模型生效，影响行数为 0 表示目标不可用）
     */
    default int setDefaultById(UUID id, UUID operatorId) {
        return update(null, new LambdaUpdateWrapperX<AiChatModel>()
                .eq(AiChatModel::getId, id)
                .eq(AiChatModel::getEnabled, true)
                .set(AiChatModel::getIsDefault, true)
                .set(AiChatModel::getUpdatedBy, operatorId)
                .set(AiChatModel::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 启用/停用
     */
    default int updateEnabledById(UUID id, boolean enabled, UUID operatorId) {
        return update(null, new LambdaUpdateWrapperX<AiChatModel>()
                .eq(AiChatModel::getId, id)
                .set(AiChatModel::getEnabled, enabled)
                .set(AiChatModel::getUpdatedBy, operatorId)
                .set(AiChatModel::getUpdatedAt, LocalDateTime.now()));
    }
}
