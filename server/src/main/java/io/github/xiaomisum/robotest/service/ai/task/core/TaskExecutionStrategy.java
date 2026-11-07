package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 一次任务执行的策略边界；策略只产出提案与结果元数据，终态由分派器统一提交。 */
public interface TaskExecutionStrategy {

    ExecutionResult execute(ExecutionContext context, ExecutionControl control);

    record ExecutionContext(AiTask task, AiTaskExecution execution, AiModelConfig model,
            UUID workspaceId, UUID projectId, String feedback, TaskTypeAdapter adapter) {
    }

    record ExecutionResult(List<ProposalDraft> proposals, Map<String, Object> resultMeta) {
        public ExecutionResult {
            proposals = proposals == null ? List.of() : List.copyOf(proposals);
        }
    }
}
