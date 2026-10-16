package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
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

    /** 项目内待确认的导入记录（详设 3.8 重复提交检查，任务状态再行核对） */
    default List<RequirementSplitRecord> selectPendingImportByProject(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<RequirementSplitRecord>()
                .eq(RequirementSplitRecord::getProjectId, projectId)
                .eq(RequirementSplitRecord::getSourceType, "document")
                .eq(RequirementSplitRecord::getStatus, "pending"));
    }

    /** 按关联任务反查拆解记录（采纳落库定位，覆盖任务重试换行场景） */
    default List<RequirementSplitRecord> selectByAiTaskId(UUID aiTaskId) {
        return selectList(new LambdaQueryWrapperX<RequirementSplitRecord>()
                .eq(RequirementSplitRecord::getAiTaskId, aiTaskId));
    }
}
