package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.TestPlanModuleSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestPlanModuleSnapshotMapper extends BaseMapperX<TestPlanModuleSnapshot> {

    default List<TestPlanModuleSnapshot> listByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId));
    }

    default void deleteByPlanId(UUID planId) {
        delete(new LambdaQueryWrapperX<TestPlanModuleSnapshot>()
                .eq(TestPlanModuleSnapshot::getPlanId, planId));
    }
}
