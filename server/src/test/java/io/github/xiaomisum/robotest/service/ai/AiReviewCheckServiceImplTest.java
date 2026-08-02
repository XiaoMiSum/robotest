package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckStartRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewCheckServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private AiTaskService aiTaskService;

    @InjectMocks
    private AiReviewCheckServiceImpl service;

    private TestReview review(String status) {
        TestReview review = new TestReview();
        review.setId(REVIEW_ID);
        review.setProjectId(PROJECT_ID);
        review.setInitiatorId(USER_ID.toString());
        review.setStatus(status);
        return review;
    }

    @Test
    void startCheck_reviewNotFound_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(null);
        assertThrows(ServiceException.class,
                () -> service.startCheck(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID));
    }

    @Test
    void startCheck_notInitiator_throws() {
        TestReview review = review(Constants.Status.IN_PROGRESS);
        review.setInitiatorId(UUID.randomUUID().toString());
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        assertThrows(ServiceException.class,
                () -> service.startCheck(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID));
    }

    @Test
    void startCheck_completed_throws() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.COMPLETED));
        assertThrows(ServiceException.class,
                () -> service.startCheck(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID));
    }

    @Test
    void startCheck_new_success() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.NEW));
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(TASK_ID);
        when(aiTaskService.createTask(Constants.AiTaskType.REVIEW_CHECK, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, USER_ID))
                .thenReturn(task);

        AiReviewCheckStartRespDTO dto = service.startCheck(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID);

        assertEquals(TASK_ID, dto.getTaskId());
        verify(aiTaskService).createTask(Constants.AiTaskType.REVIEW_CHECK, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, USER_ID);
    }

    @Test
    void startCheck_inProgress_success() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.IN_PROGRESS));
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(TASK_ID);
        when(aiTaskService.createTask(Constants.AiTaskType.REVIEW_CHECK, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, USER_ID))
                .thenReturn(task);

        AiReviewCheckStartRespDTO dto = service.startCheck(USER_ID, WORKSPACE_ID, PROJECT_ID, REVIEW_ID);

        assertEquals(TASK_ID, dto.getTaskId());
        verify(aiTaskService).createTask(Constants.AiTaskType.REVIEW_CHECK, WORKSPACE_ID, PROJECT_ID, REVIEW_ID, USER_ID);
    }

    @Test
    void getCheckResult_notInitiator_throws() {
        TestReview review = review(Constants.Status.IN_PROGRESS);
        review.setInitiatorId(UUID.randomUUID().toString());
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review);
        assertThrows(ServiceException.class, () -> service.getCheckResult(USER_ID, PROJECT_ID, REVIEW_ID));
    }

    @Test
    void getCheckResult_noRecord_returnsNull() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.IN_PROGRESS));
        when(aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, REVIEW_ID, PROJECT_ID))
                .thenReturn(null);
        assertNull(service.getCheckResult(USER_ID, PROJECT_ID, REVIEW_ID));
    }

    @Test
    void getCheckResult_returnsLatestTask() {
        when(testReviewMapper.selectById(REVIEW_ID)).thenReturn(review(Constants.Status.IN_PROGRESS));
        AiTaskRespDTO dto = new AiTaskRespDTO();
        dto.setId(TASK_ID);
        when(aiTaskService.getLatestTaskByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, REVIEW_ID, PROJECT_ID))
                .thenReturn(dto);
        assertEquals(TASK_ID, service.getCheckResult(USER_ID, PROJECT_ID, REVIEW_ID).getId());
    }
}
