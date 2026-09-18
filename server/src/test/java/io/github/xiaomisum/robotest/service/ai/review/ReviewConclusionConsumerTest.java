package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewConclusionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ReviewConclusionConsumer（06 §5.2 自动路径）：completed 评审才入队；project 缺失/评审缺失静默跳过；
 * 6005 幂等吞掉；其他异常向上抛。
 */
@ExtendWith(MockitoExtension.class)
class ReviewConclusionConsumerTest {

    @Mock
    private AiTaskService aiTaskService;
    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ReviewConclusionConsumer consumer;

    private final UUID reviewId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final UUID initiatorId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void completedReview_enqueuesTask() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setInitiatorId(initiatorId);
        review.setStatus(Constants.Status.COMPLETED);
        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceId);
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(projectMapper.selectById(projectId)).thenReturn(project);

        consumer.onReviewConclusion(new ReviewConclusionEvent(reviewId, "PASS", "通过"));

        verify(aiTaskService).createTask(Constants.AiTaskType.REVIEW_CONCLUSION,
                workspaceId, projectId, reviewId, initiatorId);
    }

    @Test
    void reviewMissing_skip() {
        when(testReviewMapper.selectById(reviewId)).thenReturn(null);

        consumer.onReviewConclusion(new ReviewConclusionEvent(reviewId, "PASS", "通过"));

        verifyNoInteractions(projectMapper);
        verifyNoInteractions(aiTaskService);
    }

    @Test
    void reviewNotCompleted_skip() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setStatus(Constants.Status.IN_PROGRESS);
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);

        consumer.onReviewConclusion(new ReviewConclusionEvent(reviewId, "PASS", "通过"));

        verifyNoInteractions(projectMapper);
        verifyNoInteractions(aiTaskService);
    }

    @Test
    void projectMissing_skip() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setStatus(Constants.Status.COMPLETED);
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(projectMapper.selectById(projectId)).thenReturn(null);

        consumer.onReviewConclusion(new ReviewConclusionEvent(reviewId, "PASS", "通过"));

        verifyNoInteractions(aiTaskService);
    }

    @Test
    void concurrentDuplicate_swallowed() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setInitiatorId(initiatorId);
        review.setStatus(Constants.Status.COMPLETED);
        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceId);
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(aiTaskService.createTask(Constants.AiTaskType.REVIEW_CONCLUSION,
                workspaceId, projectId, reviewId, initiatorId))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_DUPLICATE));

        assertDoesNotThrow(() -> consumer.onReviewConclusion(
                new ReviewConclusionEvent(reviewId, "PASS", "通过")));
    }

    @Test
    void otherException_propagates() {
        TestReview review = new TestReview();
        review.setId(reviewId);
        review.setProjectId(projectId);
        review.setInitiatorId(initiatorId);
        review.setStatus(Constants.Status.COMPLETED);
        Project project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceId);
        when(testReviewMapper.selectById(reviewId)).thenReturn(review);
        when(projectMapper.selectById(projectId)).thenReturn(project);
        when(aiTaskService.createTask(Constants.AiTaskType.REVIEW_CONCLUSION,
                workspaceId, projectId, reviewId, initiatorId))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> consumer.onReviewConclusion(new ReviewConclusionEvent(reviewId, "PASS", "通过")));
        assertEquals(ErrorCodeConstants.NO_PERMISSION.msg(), ex.getMessage());
    }
}