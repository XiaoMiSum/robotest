package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 影响分析任务处理器（AI 基座详设 3.6.1 / 追溯矩阵详设 4.3）：纯追溯边遍历，不调用模型，
 * 产物为「无」，result 只回受影响项计数；执行即刷新边上的影响处置标记（4.3）。
 */
@Component
public class ImpactAnalysisTaskHandler implements TaskHandler {

    private static final String TYPE = "impact_analysis";

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TraceMatrixService traceMatrixService;

    @Override
    public String type() {
        return TYPE;
    }

    /** 无附加资源权限（纯读取 + 标记刷新），权限与模型可用性由任务提交侧校验（3.6.2） */
    @Override
    public void validateInput(Map<String, Object> input) {
        parseRequirementId(input);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        UUID requirementId = parseRequirementId(context.getInput());
        context.report(10, "读取需求与追溯边");
        Requirement item = requirementMapper.selectById(requirementId);
        if (item == null || !Objects.equals(item.getProjectId(), context.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }

        context.report(50, "遍历受影响项并写入处置标记");
        int affectedCount = traceMatrixService.refreshImpactMarkers(context.getProjectId(), requirementId);
        context.report(100, "影响分析完成");

        // 该类型无产物（AI 基座详设 3.6.1），result 不含 artifacts，详情页按「成功无产物」展示
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("affectedCount", affectedCount);
        return new TaskResult(result, 0, 0);
    }

    private static UUID parseRequirementId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementId");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_IMPACT_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException invalid) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TRACE_IMPACT_INPUT_INVALID);
        }
    }
}
