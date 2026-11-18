package io.github.xiaomisum.robotest.service.domain.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestPlanReplanRunnerTest {

        @Mock
        private TestPlanService testPlanService;

        @InjectMocks
        private TestPlanReplanRunner runner;

        private final UUID projectId = UUID.randomUUID();
        private final UUID planId = UUID.randomUUID();

        @Test
        void run_success_marksSucceeded() {
                when(testPlanService.executeReplan(projectId, planId)).thenReturn("重新规划完成，共 5 个用例");

                runner.run(projectId, planId);

                InOrder inOrder = inOrder(testPlanService);
                inOrder.verify(testPlanService).executeReplan(projectId, planId);
                inOrder.verify(testPlanService).markReplanSucceeded(projectId, planId, "重新规划完成，共 5 个用例");
                verify(testPlanService, never()).markReplanFailed(any(), any(), any());
        }

        @Test
        void run_executeFails_marksFailed() {
                when(testPlanService.executeReplan(projectId, planId)).thenThrow(new IllegalStateException("boom"));

                runner.run(projectId, planId);

                verify(testPlanService).markReplanFailed(projectId, planId, "boom");
                verify(testPlanService, never()).markReplanSucceeded(any(), any(), any());
        }
}
