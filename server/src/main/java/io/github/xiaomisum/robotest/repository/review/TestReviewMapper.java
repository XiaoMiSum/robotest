package io.github.xiaomisum.robotest.repository.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestReviewMapper extends BaseMapperX<TestReview> {

    /** 按 ID 批量读取（追溯节点解析，详设 3.4） */
    default List<TestReview> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TestReview>().in(TestReview::getId, ids));
    }

    default PageResult<TestReview> findPage(PageParam pageParam, UUID projectId,
                                             String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TestReview>()
                .eq(TestReview::getProjectId, projectId)
                .likeIfPresent(TestReview::getTitle, keyword)
                .eqIfPresent(TestReview::getStatus, status)
                .orderByDesc(TestReview::getCreatedAt));
    }

    default long countByProjectId(UUID projectId) {
        return selectCount(TestReview::getProjectId, projectId);
    }

    default long countActiveReviews(UUID projectId) {
        return selectCount(new LambdaQueryWrapperX<TestReview>()
                .eq(TestReview::getProjectId, projectId)
                .eq(TestReview::getStatus, Constants.Status.IN_PROGRESS));
    }

    default List<TestReview> findRecentReviews(UUID projectId, int limit) {
        return selectList(new LambdaQueryWrapperX<TestReview>()
                .eq(TestReview::getProjectId, projectId)
                .orderByDesc(TestReview::getCreatedAt)
                .last("LIMIT " + limit));
    }
}
