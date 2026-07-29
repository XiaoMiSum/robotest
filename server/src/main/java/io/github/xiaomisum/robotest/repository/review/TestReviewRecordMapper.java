package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestReviewRecordMapper extends BaseMapperX<TestReviewRecord> {

    default List<TestReviewRecord> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewRecord>()
                .eq(TestReviewRecord::getReviewId, reviewId));
    }

    default List<TestReviewRecord> listByReviewIdAndNodeId(UUID reviewId, UUID snapshotNodeId) {
        return selectList(new LambdaQueryWrapperX<TestReviewRecord>()
                .eq(TestReviewRecord::getReviewId, reviewId)
                .eq(TestReviewRecord::getSnapshotNodeId, snapshotNodeId)
                .orderByAsc(TestReviewRecord::getCreatedAt));
    }

    default void deleteByReviewId(UUID reviewId) {
        delete(new LambdaQueryWrapperX<TestReviewRecord>()
                .eq(TestReviewRecord::getReviewId, reviewId));
    }
}
