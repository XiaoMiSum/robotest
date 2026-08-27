package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

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

    /** 查询流水线执行中记录（用于状态轮询） */
    default List<ApiExecutionRecord> selectRunningPipelineRecords() {
        return selectList(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getStatus, "running")
                .isNotNull(ApiExecutionRecord::getPipelineId)
                .eq(ApiExecutionRecord::getExecutionMode, "pipeline"));
    }

    /** 查询指定仓库的最近一条执行记录 */
    default ApiExecutionRecord selectLatestBySceneAndPipeline(UUID sceneId) {
        return selectOne(new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getSceneId, sceneId)
                .eq(ApiExecutionRecord::getExecutionMode, "pipeline")
                .orderByDesc(ApiExecutionRecord::getExecutedAt)
                .last("LIMIT 1"));
    }

}
