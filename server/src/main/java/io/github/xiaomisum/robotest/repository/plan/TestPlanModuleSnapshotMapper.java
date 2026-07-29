package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.plan.TestPlanModuleSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestPlanModuleSnapshotMapper extends BaseMapperX<TestPlanModuleSnapshot> {

    default List<TestPlanModuleSnapshot> listByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId));
    }

    default List<TestPlanModuleSnapshot> listSortedByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId)
                .orderByAsc(TestPlanModuleSnapshot::getSortOrder));
    }

    default List<TestPlanModuleSnapshot> listByPlanIdAndType(UUID planId, String type) {
        return selectList(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId)
                .eq(TestPlanModuleSnapshot::getType, type)
                .isNotNull(TestPlanModuleSnapshot::getOriginalModuleId));
    }

    default TestPlanModuleSnapshot findByPlanIdAndOriginalModuleId(UUID planId, UUID originalModuleId) {
        return selectOne(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId)
                .eq(TestPlanModuleSnapshot::getOriginalModuleId, originalModuleId));
    }

    default void deleteByPlanId(UUID planId) {
        delete(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId));
    }
}
