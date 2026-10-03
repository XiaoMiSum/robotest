package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface RequirementSplitRecordMapper extends BaseMapperX<RequirementSplitRecord> {

    /** 项目内拆解记录列表（详设 3.11，参数 status / sourceType / 分页） */
    default PageResult<RequirementSplitRecord> findPage(PageParam pageParam, UUID projectId, String status,
            String sourceType) {
        return selectPage(pageParam, new LambdaQueryWrapperX<RequirementSplitRecord>()
                .eq(RequirementSplitRecord::getProjectId, projectId)
                .eqIfPresent(RequirementSplitRecord::getStatus, status)
                .eqIfPresent(RequirementSplitRecord::getSourceType, sourceType)
                .orderByDesc(RequirementSplitRecord::getCreatedAt));
    }

    /** 按原条目反查拆解记录（详设 3.11 split-logs，idx_requirement_split_source） */
    default PageResult<RequirementSplitRecord> findPageBySourceRequirementId(PageParam pageParam,
            UUID sourceRequirementId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<RequirementSplitRecord>()
                .eq(RequirementSplitRecord::getSourceRequirementId, sourceRequirementId)
                .orderByDesc(RequirementSplitRecord::getCreatedAt));
    }
}
