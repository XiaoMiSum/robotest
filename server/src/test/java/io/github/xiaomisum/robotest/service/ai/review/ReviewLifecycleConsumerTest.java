package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.github.xiaomisum.robotest.service.project.review.ReviewLifecycleEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewLifecycleConsumerTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();

    @Mock
    private AiTaskService aiTaskService;

    @InjectMocks
    private ReviewLifecycleConsumer consumer;

    @Test
    void onReviewLifecycle_cancelsReviewCheckTask() {
        consumer.onReviewLifecycle(new ReviewLifecycleEvent(REVIEW_ID));

        verify(aiTaskService).cancelByTypeAndTarget(Constants.AiTaskType.REVIEW_CHECK, REVIEW_ID);
    }
}