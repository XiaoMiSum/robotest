package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

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

}
