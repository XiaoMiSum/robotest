package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface ApiDebugRecordMapper extends BaseMapperX<ApiDebugRecord> {

    /** 历史记录分页：仅当前用户，按执行时间倒序，keyword 匹配名称或 URL */
    default PageResult<ApiDebugRecord> selectPage(UUID projectId, UUID userId, String keyword, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiDebugRecord>()
                .eq(ApiDebugRecord::getProjectId, projectId)
                .eq(ApiDebugRecord::getUserId, userId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(ApiDebugRecord::getName, keyword)
                        .or()
                        .like(ApiDebugRecord::getUrl, keyword))
                .orderByDesc(ApiDebugRecord::getExecutedAt));
    }

    /** 自动保存后淘汰：删除该用户超出保留上限的最旧记录 */
    default void trimToLimit(UUID projectId, UUID userId, int limit) {
        Long total = selectCount(new LambdaQueryWrapperX<ApiDebugRecord>()
                .eq(ApiDebugRecord::getProjectId, projectId)
                .eq(ApiDebugRecord::getUserId, userId));
        if (total == null || total <= limit) {
            return;
        }
        selectList(new LambdaQueryWrapperX<ApiDebugRecord>()
                .eq(ApiDebugRecord::getProjectId, projectId)
                .eq(ApiDebugRecord::getUserId, userId)
                .orderByDesc(ApiDebugRecord::getExecutedAt)
                .last("OFFSET " + limit))
                .forEach(this::deleteById);
    }
}
