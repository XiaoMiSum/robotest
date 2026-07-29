package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestReviewModuleSnapshotMapper extends BaseMapperX<TestReviewModuleSnapshot> {

    default List<TestReviewModuleSnapshot> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId));
    }

    default List<TestReviewModuleSnapshot> listByReviewIdAndType(UUID reviewId, String type) {
        return selectList(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                .eq(TestReviewModuleSnapshot::getType, type)
                .isNotNull(TestReviewModuleSnapshot::getOriginalModuleId));
    }

    default TestReviewModuleSnapshot findByReviewIdAndOriginalModuleId(UUID reviewId, UUID originalModuleId) {
        return selectOne(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                .eq(TestReviewModuleSnapshot::getOriginalModuleId, originalModuleId));
    }

    default void deleteByReviewId(UUID reviewId) {
        delete(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId));
    }
}
