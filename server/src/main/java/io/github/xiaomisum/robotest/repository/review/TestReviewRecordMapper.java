package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.TestReviewRecord;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestReviewRecordMapper extends BaseMapperX<TestReviewRecord> {

    default List<TestReviewRecord> listByReviewId(UUID reviewId) {
        return selectList(new LambdaQueryWrapperX<TestReviewRecord>()
                .eq(TestReviewRecord::getReviewId, reviewId));
    }
}
