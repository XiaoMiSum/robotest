package io.github.xiaomisum.robotest.service.domain.review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestReviewReplanRunnerTest {

        @Mock
        private TestReviewService testReviewService;

        @InjectMocks
        private TestReviewReplanRunner runner;

        private final UUID projectId = UUID.randomUUID();
        private final UUID reviewId = UUID.randomUUID();

        @Test
        void run_success_marksSucceeded() {
                when(testReviewService.executeReplan(projectId, reviewId)).thenReturn("重新规划完成，共 3 个用例");

                runner.run(projectId, reviewId);

                InOrder inOrder = inOrder(testReviewService);
                inOrder.verify(testReviewService).executeReplan(projectId, reviewId);
                inOrder.verify(testReviewService).markReplanSucceeded(projectId, reviewId, "重新规划完成，共 3 个用例");
                verify(testReviewService, never()).markReplanFailed(any(), any(), any());
        }

        @Test
        void run_executeFails_marksFailed() {
                when(testReviewService.executeReplan(projectId, reviewId)).thenThrow(new IllegalStateException("boom"));

                runner.run(projectId, reviewId);

                verify(testReviewService).markReplanFailed(projectId, reviewId, "boom");
                verify(testReviewService, never()).markReplanSucceeded(any(), any(), any());
        }
}
