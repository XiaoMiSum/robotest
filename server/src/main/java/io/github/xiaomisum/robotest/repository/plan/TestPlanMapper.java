package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.TestPlan;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface TestPlanMapper extends BaseMapperX<TestPlan> {

    default PageResult<TestPlan> findPage(PageParam pageParam, UUID projectId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .orderByDesc(TestPlan::getCreatedAt));
    }

    default long countByProjectId(UUID projectId) {
        return selectCount(TestPlan::getProjectId, projectId);
    }
}
