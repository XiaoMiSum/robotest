package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestPlanMapper extends BaseMapperX<TestPlan> {

    default PageResult<TestPlan> findPage(PageParam pageParam, UUID projectId,
                                           String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .likeIfPresent(TestPlan::getName, keyword)
                .eqIfPresent(TestPlan::getStatus, status)
                .orderByDesc(TestPlan::getCreatedAt));
    }

    default long countByProjectId(UUID projectId) {
        return selectCount(TestPlan::getProjectId, projectId);
    }

    default long countActiveByProjectId(UUID projectId, Collection<String> activeStatuses) {
        return selectCount(new LambdaQueryWrapperX<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .in(TestPlan::getStatus, activeStatuses));
    }

    default long countActivePlans(UUID projectId) {
        return selectCount(new LambdaQueryWrapperX<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .in(TestPlan::getStatus, Constants.Status.NEW, Constants.Status.IN_PROGRESS));
    }

    default List<TestPlan> findRecentPlans(UUID projectId, int limit) {
        return selectList(new LambdaQueryWrapperX<TestPlan>()
                .eq(TestPlan::getProjectId, projectId)
                .orderByDesc(TestPlan::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
