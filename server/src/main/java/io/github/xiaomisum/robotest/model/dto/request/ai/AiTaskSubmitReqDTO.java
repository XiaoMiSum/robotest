package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 提交 AI 任务（POST /api/ai/tasks，详设 3.6.2，创建即启动执行）。
 */
@Data
public class AiTaskSubmitReqDTO {

    /** 任务类型（详设 3.6.1 枚举，未注册为 1000018114） */
    @NotBlank(message = "任务类型不能为空")
    private String type;

    /** 任务输入，按 type 分发校验（1000018115） */
    private Map<String, Object> input;

    /**
     * 预算覆盖 {maxSteps, maxDurationSeconds, maxTokens, maxCost}：仅可逐维低于类型默认值，
     * 未知维度 / 超上限 / 非正数为 1000018126（校验在 BudgetGuard 签发时执行）。
     */
    private Map<String, Object> budget;

    /** 同步等待秒数 ∈ [0, 10]（超出为 1000018123，服务层校验），0 表示立即返回转轮询 */
    private Integer waitSeconds;
}
