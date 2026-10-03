package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ErrorCode;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Map;
import java.util.UUID;

/**
 * 任务执行引擎（详设 4.2）：pending → running → succeeded / failed 的状态流转全部走条件更新
 * （CAS），与用户取消（3.6.4）竞争时以先到者为准。独立组件承载 @Async，规避 Service 自调用失效。
 */
@Component
public class AiTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiTaskExecutor.class);

    private static final int ERROR_MSG_MAX_LENGTH = 500;

    @Resource
    private AiTaskMapper taskMapper;
    @Resource
    private TaskHandlerRegistry handlerRegistry;
    @Resource
    private AiSettingsReader settingsReader;
    @Resource
    private AiModelClient modelClient;
    @Resource
    private AiPromptService promptService;

    /**
     * 异步起跑：活动事务内由服务侧注册 afterCommit 兜底再次触发，本方法经 casStart 条件写
     * 保证任务至多起跑一次；行未提交时 CAS 0 行静默返回（详见 AiTaskServiceImpl.startTask）。
     */
    @Async
    public void start(UUID taskId) {
        execute(taskId);
    }

    void execute(UUID taskId) {
        if (!taskMapper.casStart(taskId)) {
            // 任务不存在或已被取消：不落状态
            return;
        }
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (!settingsReader.settings().enabled()) {
            fail(taskId, ErrorCodeConstants.AI_DISABLED);
            return;
        }
        TaskHandler handler = handlerRegistry.get(task.getType());
        if (handler == null) {
            fail(taskId, ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED);
            return;
        }
        AiModelConfig model = settingsReader.resolveModel(task.getModelId());
        if (model == null) {
            fail(taskId, ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED);
            return;
        }
        TaskExecutionContext context = new TaskExecutionContext(
                task.getId(), task.getProjectId(), task.getWorkspaceId(), task.getSubmittedBy(),
                task.getPromptScene(), model, task.getInput() == null ? Map.of() : task.getInput(),
                (percent, phase) -> taskMapper.reportProgress(taskId, percent, phase),
                modelClient, promptService);
        try {
            TaskResult result = handler.execute(context);
            taskMapper.casSucceed(taskId, result == null ? Map.of() : result.result(),
                    result == null ? 0 : result.tokensIn(), result == null ? 0 : result.tokensOut());
        } catch (ServiceException e) {
            fail(taskId, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("[AI] 任务执行异常 taskId={} type={}", taskId, task.getType(), e);
            taskMapper.casFail(taskId, ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    truncate(e.getMessage() == null ? ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg() : e.getMessage()));
        }
    }

    private void fail(UUID taskId, ErrorCode errorCode) {
        fail(taskId, errorCode.code(), errorCode.msg());
    }

    private void fail(UUID taskId, int errorCode, String errorMsg) {
        taskMapper.casFail(taskId, errorCode, truncate(errorMsg));
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MSG_MAX_LENGTH ? message.substring(0, ERROR_MSG_MAX_LENGTH) : message;
    }
}
