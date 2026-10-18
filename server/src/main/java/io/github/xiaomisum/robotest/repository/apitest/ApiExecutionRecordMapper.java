package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface ApiExecutionRecordMapper extends BaseMapperX<ApiExecutionRecord> {

    /** 场景执行历史：时间倒序 */
    default PageResult<ApiExecutionRecord> selectPageByScene(UUID sceneId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getSceneId, sceneId)
                .orderByDesc(ApiExecutionRecord::getExecutedAt));
    }

    /** 一次取回页内记录，避免列表装配逐场景查询。 */
    default List<ApiExecutionRecord> listLatestBySceneIds(Collection<UUID> sceneIds) {
        if (sceneIds == null || sceneIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .in(ApiExecutionRecord::getSceneId, sceneIds)
                .orderByDesc(ApiExecutionRecord::getExecutedAt));
    }

    /** 批次快照读取（GitLab 集成详设 4.6 取清单）：同一批次的冻结记录，id 序稳定返回 */
    default List<ApiExecutionRecord> selectByBatchId(String batchId) {
        return selectList(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getBatchId, batchId)
                .orderByAsc(ApiExecutionRecord::getId));
    }

    /** 触发成功回写：整批补流水线编号与链接并置 running（C11：wrapper 显式 set） */
    default int markBatchRunning(String batchId, Long pipelineIid, String pipelineUrl) {
        LambdaUpdateWrapperX<ApiExecutionRecord> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiExecutionRecord::getBatchId, batchId)
                .eq(ApiExecutionRecord::getStatus, "pending")
                .set(ApiExecutionRecord::getPipelineIid, pipelineIid)
                .set(ApiExecutionRecord::getPipelineUrl, pipelineUrl)
                .set(ApiExecutionRecord::getStatus, "running");
        return update(null, wrapper);
    }

    /** 触发失败留痕：整批置 error（流水线未起来，批次无后续通道，error 即终态） */
    default int markBatchError(String batchId, String errorMessage) {
        LambdaUpdateWrapperX<ApiExecutionRecord> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiExecutionRecord::getBatchId, batchId)
                .eq(ApiExecutionRecord::getStatus, "pending")
                .set(ApiExecutionRecord::getStatus, "error")
                .set(ApiExecutionRecord::getErrorMessage, errorMessage);
        return update(null, wrapper);
    }

    /**
     * 轮询输入（GitLab 集成详设 4.2 第 6 步）：未达终态且已关联流水线的批次记录；
     * 无内存态，进程重启后本查询即扫描恢复入口（BR-1.29），走 idx_exec_status / idx_exec_pipeline。
     */
    default List<ApiExecutionRecord> selectPollableRecords() {
        return selectList(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getStatus, "running")
                .isNotNull(ApiExecutionRecord::getPipelineIid));
    }

    /** 混合任务完成判据输入（GitLab 集成详设 4.4）：任务名下的流水线批次记录，按冻结先后升序 */
    default List<ApiExecutionRecord> listBatchRecordsByTask(UUID taskId) {
        return selectList(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getScheduledTaskId, taskId)
                .isNotNull(ApiExecutionRecord::getBatchId)
                .orderByAsc(ApiExecutionRecord::getCreatedAt));
    }

    /**
     * 终态写闸门（GitLab 集成详设 4.2 第 5 步终态去重）：条件更新仅 pending/running 生效——
     * 命中 0 行即批次已置终态，CI 回报与轮询补拉两通道后到写入一律忽略，不覆盖先到结果。
     *
     * @return 实际更新的记录数，0 表示终态去重命中
     */
    default int markBatchTerminal(String batchId, String status, String errorMessage) {
        return markBatchTerminal(batchId, status, errorMessage, null);
    }

    /** 同上；整批降级时随终态一并写入对账摘要（summary 为 null 不触碰该列） */
    default int markBatchTerminal(String batchId, String status, String errorMessage,
            Map<String, Object> summary) {
        LambdaUpdateWrapperX<ApiExecutionRecord> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiExecutionRecord::getBatchId, batchId)
                .in(ApiExecutionRecord::getStatus, "pending", "running")
                .set(ApiExecutionRecord::getStatus, status)
                .set(ApiExecutionRecord::getErrorMessage, errorMessage);
        if (summary != null) {
            wrapper.set(ApiExecutionRecord::getSummary, toJsonOrNull(summary));
        }
        return update(null, wrapper);
    }

    /**
     * 记录级条件终态（结果回收回写，GitLab 集成详设 4.2 第 6 步）：与批次级闸门同一
     * pending/running 谓词——命中 0 行即另一通道已抢先终态化，调用方据以放弃本次结果。
     * 终态语义由本次写入完整承载，errorMessage / reportId / durationMs 显式置空即清空（C11）。
     *
     * @return 实际更新的记录数，0 表示终态去重命中
     */
    default int finishRecord(UUID recordId, String status, String errorMessage, UUID reportId,
            Integer durationMs, Map<String, Object> summary) {
        LambdaUpdateWrapperX<ApiExecutionRecord> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiExecutionRecord::getId, recordId)
                .in(ApiExecutionRecord::getStatus, "pending", "running")
                .set(ApiExecutionRecord::getStatus, status)
                .set(ApiExecutionRecord::getErrorMessage, errorMessage)
                .set(ApiExecutionRecord::getReportId, reportId)
                .set(ApiExecutionRecord::getDurationMs, durationMs)
                .set(ApiExecutionRecord::getSummary, toJsonOrNull(summary));
        return update(null, wrapper);
    }

    /**
     * CI 回报计数合入（GitLab 集成详设 4.6）：仅 pending/running 生效——批次已终态则
     * 本次写入被忽略（BR-1.24 去重口径）。summary 为整值替换，调用方须先读再合并。
     *
     * @return 实际更新的记录数，0 表示终态去重命中
     */
    default int updateSummaryIfRunning(UUID recordId, Map<String, Object> summary) {
        LambdaUpdateWrapperX<ApiExecutionRecord> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiExecutionRecord::getId, recordId)
                .in(ApiExecutionRecord::getStatus, "pending", "running")
                .set(ApiExecutionRecord::getSummary, toJsonOrNull(summary));
        return update(null, wrapper);
    }

    /**
     * JSON 列经 wrapper.set 写入时没有 typeHandler 参与，Map 原样交给 pgjdbc 会被按 hstore
     * 解析而抛 "No hstore extension installed"，须传序列化后的字符串（与实体路径
     * Jackson3TypeHandler → setString 的绑定语义一致）；null 必须保持 null 以显式清空列，
     * 不能用 JsonUtils.toJsonString（其 null 入参返回 "{}"）。
     */
    private static String toJsonOrNull(Map<String, Object> summary) {
        return summary == null ? null : JsonUtils.toJsonString(summary);
    }

}
