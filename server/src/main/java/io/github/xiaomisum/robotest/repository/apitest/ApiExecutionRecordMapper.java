package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

@Mapper
public interface ApiExecutionRecordMapper extends BaseMapperX<ApiExecutionRecord> {

    /** 场景执行历史：时间倒序 */
    default PageResult<ApiExecutionRecord> selectPageByScene(UUID sceneId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiExecutionRecord>()
                .eq(ApiExecutionRecord::getSceneId, sceneId)
                .orderByDesc(ApiExecutionRecord::getExecutedAt));
    }

}
