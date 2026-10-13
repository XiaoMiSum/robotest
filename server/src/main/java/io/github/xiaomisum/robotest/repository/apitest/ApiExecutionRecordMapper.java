package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.Collection;
import java.util.List;
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

}
