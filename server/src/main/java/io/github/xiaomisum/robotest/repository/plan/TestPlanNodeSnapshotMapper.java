package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.TestPlanNodeSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestPlanNodeSnapshotMapper extends BaseMapperX<TestPlanNodeSnapshot> {

    default List<TestPlanNodeSnapshot> listByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId));
    }

    default void deleteByPlanId(UUID planId) {
        delete(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId));
    }
}
