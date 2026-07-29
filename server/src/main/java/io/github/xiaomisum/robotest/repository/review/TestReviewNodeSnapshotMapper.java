package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.TestReviewNodeSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestReviewNodeSnapshotMapper extends BaseMapperX<TestReviewNodeSnapshot> {

    default List<TestReviewNodeSnapshot> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId));
    }
}
