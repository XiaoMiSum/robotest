package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.TestReviewModuleSnapshot;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestReviewModuleSnapshotMapper extends BaseMapperX<TestReviewModuleSnapshot> {

    default List<TestReviewModuleSnapshot> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId));
    }
}
