package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.RequirementChangeLog;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface RequirementChangeLogMapper extends BaseMapperX<RequirementChangeLog> {

    /** 变更记录时间线倒序拉取（idx_requirement_change_log_req） */
    default PageResult<RequirementChangeLog> findPage(PageParam pageParam, UUID requirementId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<RequirementChangeLog>()
                .eq(RequirementChangeLog::getRequirementId, requirementId)
                .orderByDesc(RequirementChangeLog::getCreatedAt));
    }
}
