package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.TestReview;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface TestReviewMapper extends BaseMapperX<TestReview> {

    default PageResult<TestReview> findPage(PageParam pageParam, UUID projectId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TestReview>()
                .eq(TestReview::getProjectId, projectId)
                .orderByDesc(TestReview::getCreatedAt));
    }

    default long countByProjectId(UUID projectId) {
        return selectCount(TestReview::getProjectId, projectId);
    }
}
