package io.github.xiaomisum.robotest.repository.trace;

import io.github.xiaomisum.robotest.model.entity.trace.TraceCoverageResult;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TraceCoverageResultMapper extends BaseMapperX<TraceCoverageResult> {

    /** 覆盖状态查询（详设 3.7）：不传 requirementIds 时按项目全量分页 */
    default PageResult<TraceCoverageResult> findPage(PageParam pageParam, UUID projectId,
            Collection<UUID> requirementIds) {
        LambdaQueryWrapperX<TraceCoverageResult> wrapper = new LambdaQueryWrapperX<TraceCoverageResult>()
                .eq(TraceCoverageResult::getProjectId, projectId);
        if (requirementIds != null && !requirementIds.isEmpty()) {
            wrapper.in(TraceCoverageResult::getRequirementId, requirementIds);
        }
        wrapper.orderByDesc(TraceCoverageResult::getUpdatedAt);
        return selectPage(pageParam, wrapper);
    }

    /** 需求列表 / 详情批量填充覆盖状态（无记录即「待分析」，由调用方推导） */
    default List<TraceCoverageResult> listByRequirementIds(UUID projectId, Collection<UUID> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TraceCoverageResult>()
                .eq(TraceCoverageResult::getProjectId, projectId)
                .in(TraceCoverageResult::getRequirementId, requirementIds));
    }

    default TraceCoverageResult findByRequirement(UUID projectId, UUID requirementId) {
        return selectOne(new LambdaQueryWrapperX<TraceCoverageResult>()
                .eq(TraceCoverageResult::getProjectId, projectId)
                .eq(TraceCoverageResult::getRequirementId, requirementId));
    }
}
