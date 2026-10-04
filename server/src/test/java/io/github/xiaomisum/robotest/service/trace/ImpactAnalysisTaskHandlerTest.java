package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 影响分析任务处理器单测：输入校验与纯边遍历执行（追溯矩阵详设 4.3，C8）。
 */
@ExtendWith(MockitoExtension.class)
class ImpactAnalysisTaskHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TraceMatrixService traceMatrixService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private ImpactAnalysisTaskHandler handler;

    @Test
    void type_isImpactAnalysis() {
        assertEquals("impact_analysis", handler.type());
    }

    @Test
    void validateInput_missingRequirementId_throwsInputInvalid() {
        assertInputInvalid(() -> handler.validateInput(Map.of()));
        assertInputInvalid(() -> handler.validateInput(null));
    }

    @Test
    void validateInput_malformedUuid_throwsInputInvalid() {
        assertInputInvalid(() -> handler.validateInput(Map.of("requirementId", "not-a-uuid")));
    }

    @Test
    void validateInput_validId_passes() {
        assertDoesNotThrow(() -> handler.validateInput(
                Map.of("requirementId", REQUIREMENT_ID.toString())));
    }

    @Test
    void execute_refreshesMarkersAndReturnsAffectedCount() {
        Requirement item = new Requirement();
        item.setId(REQUIREMENT_ID);
        item.setProjectId(PROJECT_ID);
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(item);
        when(context.getInput()).thenReturn(Map.of("requirementId", REQUIREMENT_ID.toString()));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(traceMatrixService.refreshImpactMarkers(PROJECT_ID, REQUIREMENT_ID)).thenReturn(4);

        TaskResult result = handler.execute(context);

        assertEquals(4, result.result().get("affectedCount"));
        assertEquals(0, result.tokensIn());
        assertEquals(0, result.tokensOut());
        verify(context, atLeastOnce()).report(anyInt(), anyString());
    }

    @Test
    void execute_requirementMissing_throwsRequirementNotFound() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(null);
        when(context.getInput()).thenReturn(Map.of("requirementId", REQUIREMENT_ID.toString()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
    }

    /** 业务异常断言：校验失败统一 1000018157（影响分析任务参数非法） */
    private void assertInputInvalid(Runnable invocation) {
        ServiceException exception =
                assertThrows(ServiceException.class, invocation::run);
        assertEquals(ErrorCodeConstants.TRACE_IMPACT_INPUT_INVALID.code(), exception.getCode());
    }
}
