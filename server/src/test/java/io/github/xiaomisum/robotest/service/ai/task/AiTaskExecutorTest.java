package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskExecutorTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final String TYPE = "requirement_split";

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private TaskHandlerRegistry handlerRegistry;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AiPromptService promptService;
    @Mock
    private TaskHandler handler;

    @InjectMocks
    private AiTaskExecutor executor;

    @BeforeEach
    void setUp() {
        lenient().when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, MODEL_ID, 600, 2));
        lenient().when(settingsReader.resolveModel(any())).thenReturn(model());
        lenient().when(handlerRegistry.get(TYPE)).thenReturn(handler);
    }

    private static AiModelConfig model() {
        AiModelConfig model = new AiModelConfig();
        model.setId(MODEL_ID);
        model.setEnabled(true);
        return model;
    }

    private static AiTask pendingTask() {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setType(TYPE);
        task.setStatus("pending");
        task.setModelId(MODEL_ID);
        task.setInput(Map.of("requirementId", "r-1"));
        task.setPromptScene(TYPE);
        return task;
    }

    @Test
    void execute_casStartFails_skipsSilently() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(false);

        executor.start(TASK_ID);

        verify(taskMapper, never()).selectById(any());
        verifyNoInteractions(handlerRegistry, modelClient, promptService);
    }

    @Test
    void execute_switchOffMidRun_fails101() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));

        executor.start(TASK_ID);

        verify(taskMapper).casFail(TASK_ID, ErrorCodeConstants.AI_DISABLED.code(),
                ErrorCodeConstants.AI_DISABLED.msg());
        verifyNoInteractions(handler);
    }

    @Test
    void execute_handlerMissing_fails114() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(handlerRegistry.get(TYPE)).thenReturn(null);

        executor.start(TASK_ID);

        verify(taskMapper).casFail(TASK_ID, ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(),
                ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.msg());
    }

    @Test
    void execute_modelUnavailable_fails118() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(settingsReader.resolveModel(any())).thenReturn(null);

        executor.start(TASK_ID);

        verify(taskMapper).casFail(TASK_ID, ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED.code(),
                ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED.msg());
        verifyNoInteractions(handler);
    }

    @Test
    void execute_handlerSucceeds_writesResultAndTokens() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        Map<String, Object> result = Map.of("artifacts", List.of(Map.of("key", "a1")));
        when(handler.execute(any())).thenReturn(new TaskResult(result, 11, 22));

        executor.start(TASK_ID);

        verify(taskMapper).casSucceed(TASK_ID, result, 11, 22);
    }

    @Test
    void execute_progressReportedThroughContext() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(taskMapper.reportProgress(TASK_ID, 50, "生成中")).thenReturn(true);
        doAnswer(invocation -> {
            TaskExecutionContext context = invocation.getArgument(0);
            context.report(50, "生成中");
            context.report(90, "收尾");
            return new TaskResult(Map.of(), 0, 0);
        }).when(handler).execute(any());

        executor.start(TASK_ID);

        verify(taskMapper).reportProgress(TASK_ID, 50, "生成中");
        verify(taskMapper).reportProgress(TASK_ID, 90, "收尾");
        verify(taskMapper).casSucceed(eq(TASK_ID), any(), anyInt(), anyInt());
    }

    @Test
    void execute_handlerThrowsBusinessException_failsWithItsCode() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(handler.execute(any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID));

        executor.start(TASK_ID);

        verify(taskMapper).casFail(TASK_ID, ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                ErrorCodeConstants.AI_TASK_INPUT_INVALID.msg());
        verify(taskMapper, never()).casSucceed(any(), any(), anyInt(), anyInt());
    }

    @Test
    void execute_handlerThrowsUnexpected_fails117WithTruncatedMessage() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        String longMessage = "x".repeat(600);
        when(handler.execute(any())).thenThrow(new IllegalStateException(longMessage));

        executor.start(TASK_ID);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskMapper).casFail(eq(TASK_ID), eq(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code()),
                messageCaptor.capture());
        assertEquals(500, messageCaptor.getValue().length());
    }

    @Test
    void execute_cancellationRace_resultWriteIsConditional() {
        // 取消先到：casSucceed 条件写 0 行，不覆盖 cancelled —— 通过 Mapper 契约验证
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        when(taskMapper.selectById(TASK_ID)).thenReturn(pendingTask());
        when(handler.execute(any())).thenReturn(new TaskResult(Map.of(), 0, 0));
        when(taskMapper.casSucceed(eq(TASK_ID), any(), anyInt(), anyInt())).thenReturn(false);

        executor.start(TASK_ID);

        verify(taskMapper).casSucceed(eq(TASK_ID), any(), anyInt(), anyInt());
        verify(taskMapper, never()).casFail(any(), anyInt(), anyString());
    }

    @Test
    void execute_contextCarriesTaskScopeAndModel() {
        when(taskMapper.casStart(TASK_ID)).thenReturn(true);
        AiTask task = pendingTask();
        UUID projectId = UUID.randomUUID();
        task.setProjectId(projectId);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        ArgumentCaptor<TaskExecutionContext> captor = ArgumentCaptor.forClass(TaskExecutionContext.class);
        when(handler.execute(captor.capture())).thenReturn(new TaskResult(Map.of(), 0, 0));

        executor.start(TASK_ID);

        TaskExecutionContext context = captor.getValue();
        assertEquals(TASK_ID, context.getTaskId());
        assertEquals(projectId, context.getProjectId());
        assertEquals(TYPE, context.getPromptScene());
        assertEquals(MODEL_ID, context.getModel().getId());
        assertNotNull(context.getInput());
        // 上下文持有模型客户端与提示词服务，供 handler 内部调用
        assertEquals(modelClient, context.getModelClient());
        assertEquals(promptService, context.getPromptService());
    }
}
