package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.plan.TestPlanExecutionRecord;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestPlanExecutionRecordMapper extends BaseMapperX<TestPlanExecutionRecord> {

    default List<TestPlanExecutionRecord> listByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanExecutionRecord>()
                .eq(TestPlanExecutionRecord::getPlanId, planId));
    }

    default List<TestPlanExecutionRecord> listByPlanIdAndNodeId(UUID planId, UUID snapshotNodeId) {
        return selectList(new LambdaQueryWrapperX<TestPlanExecutionRecord>()
                .eq(TestPlanExecutionRecord::getPlanId, planId)
                .eq(TestPlanExecutionRecord::getSnapshotNodeId, snapshotNodeId)
                .orderByAsc(TestPlanExecutionRecord::getExecutedAt));
    }

    default void deleteByPlanId(UUID planId) {
        delete(new LambdaQueryWrapperX<TestPlanExecutionRecord>()
                .eq(TestPlanExecutionRecord::getPlanId, planId));
    }
}
