package io.github.xiaomisum.robotest.repository.plan;

import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestPlanNodeSnapshotMapper extends BaseMapperX<TestPlanNodeSnapshot> {

    default List<TestPlanNodeSnapshot> listByPlanId(UUID planId) {
        return selectList(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId));
    }

    default List<TestPlanNodeSnapshot> listByPlanIdAndDocumentId(UUID planId, UUID documentSnapshotId) {
        LambdaQueryWrapperX<TestPlanNodeSnapshot> wrapper = new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId);
        if (documentSnapshotId != null) {
            wrapper.eq(TestPlanNodeSnapshot::getDocumentSnapshotId, documentSnapshotId);
        }
        return selectList(wrapper);
    }

    default List<TestPlanNodeSnapshot> listAssociatedByPlanIds(Collection<UUID> planIds, String type) {
        return selectList(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .in(TestPlanNodeSnapshot::getPlanId, planIds)
                .eq(TestPlanNodeSnapshot::getIsAssociated, true)
                .eqIfPresent(TestPlanNodeSnapshot::getType, type));
    }

    default List<TestPlanNodeSnapshot> listAssociatedByPlanId(UUID planId, String type) {
        return selectList(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId)
                .eq(TestPlanNodeSnapshot::getIsAssociated, true)
                .eqIfPresent(TestPlanNodeSnapshot::getType, type));
    }

    default List<TestPlanNodeSnapshot> listAssociatedByPlanIdAndDocumentId(UUID planId, UUID documentSnapshotId) {
        return selectList(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId)
                .eq(TestPlanNodeSnapshot::getDocumentSnapshotId, documentSnapshotId)
                .eq(TestPlanNodeSnapshot::getIsAssociated, true));
    }

    default long countUntestedAssociatedByPlanId(UUID planId, String untestedResult) {
        return selectCount(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId)
                .eq(TestPlanNodeSnapshot::getIsAssociated, true)
                .eq(TestPlanNodeSnapshot::getLastResult, untestedResult));
    }

    default void deleteByPlanIdAndDocumentId(UUID planId, UUID documentSnapshotId) {
        delete(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId)
                .eq(TestPlanNodeSnapshot::getDocumentSnapshotId, documentSnapshotId));
    }

    default void deleteByPlanId(UUID planId) {
        delete(new LambdaQueryWrapperX<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId));
    }
}
