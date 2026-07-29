package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestReviewNodeSnapshotMapper extends BaseMapperX<TestReviewNodeSnapshot> {

    default List<TestReviewNodeSnapshot> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId));
    }

    default List<TestReviewNodeSnapshot> listByReviewIdAndDocumentId(UUID reviewId, UUID documentSnapshotId) {
        LambdaQueryWrapperX<TestReviewNodeSnapshot> wrapper = new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId);
        if (documentSnapshotId != null) {
            wrapper.eq(TestReviewNodeSnapshot::getDocumentSnapshotId, documentSnapshotId);
        }
        return selectList(wrapper);
    }

    default List<TestReviewNodeSnapshot> listAssociatedByReviewIds(Collection<UUID> reviewIds, String type) {
        return selectList(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .in(TestReviewNodeSnapshot::getReviewId, reviewIds)
                .eq(TestReviewNodeSnapshot::getIsAssociated, true)
                .eqIfPresent(TestReviewNodeSnapshot::getType, type));
    }

    default List<TestReviewNodeSnapshot> listAssociatedByReviewId(UUID reviewId, String type) {
        return selectList(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                .eq(TestReviewNodeSnapshot::getIsAssociated, true)
                .eqIfPresent(TestReviewNodeSnapshot::getType, type));
    }

    default List<TestReviewNodeSnapshot> listAssociatedByReviewIdAndDocumentId(UUID reviewId, UUID documentSnapshotId) {
        return selectList(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                .eq(TestReviewNodeSnapshot::getDocumentSnapshotId, documentSnapshotId)
                .eq(TestReviewNodeSnapshot::getIsAssociated, true));
    }

    default void deleteByReviewIdAndDocumentId(UUID reviewId, UUID documentSnapshotId) {
        delete(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                .eq(TestReviewNodeSnapshot::getDocumentSnapshotId, documentSnapshotId));
    }

    default void deleteByReviewId(UUID reviewId) {
        delete(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId));
    }

    default void resetLastMarkAsPending(UUID snapshotNodeId, UUID reviewerId, LocalDateTime reviewedAt) {
        update(null, new LambdaUpdateWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getId, snapshotNodeId)
                .set(TestReviewNodeSnapshot::getLastMark, null)
                .set(TestReviewNodeSnapshot::getLastReviewerId, reviewerId)
                .set(TestReviewNodeSnapshot::getLastReviewedAt, reviewedAt));
    }
}
