package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

@Mapper
public interface ApiChangeHistoryMapper extends BaseMapperX<ApiChangeHistory> {

    /** 变更历史只读分页：版本倒序（基础设施详细设计 2.1.2 索引口径） */
    default PageResult<ApiChangeHistory> selectPageByTarget(String targetType, UUID targetId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiChangeHistory>()
                .eq(ApiChangeHistory::getTargetType, targetType)
                .eq(ApiChangeHistory::getTargetId, targetId)
                .orderByDesc(ApiChangeHistory::getVersion));
    }

    @Select("SELECT COALESCE(MAX(version), 0) FROM api_change_history "
            + "WHERE target_type = #{targetType} AND target_id = #{targetId} AND is_deleted = FALSE")
    int selectMaxVersion(String targetType, UUID targetId);

}
