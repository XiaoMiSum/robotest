package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 任务执行（详设 2.6.2）：一次执行一条，再次执行新建行并保留原执行与轨迹；
 * 心跳与预算是执行期护栏的落点（预算耗尽 1000018127、心跳超时 1000018117）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task_execution", autoResultMap = true)
public class AiTaskExecution extends BaseUuidDO<AiTaskExecution> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;

    /** 同任务内第几次执行（首次 1，再次执行自增） */
    private Integer seq;

    /** running / waiting_confirmation / succeeded / aborted（详设 2.6.2） */
    private String status;

    /** 执行发起人 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID startedBy;

    /** 实际使用的提示词场景，用量按场景归因 */
    private String promptScene;

    /** 执行期解析的模型，再次执行复用同模型 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID modelId;

    /** AI 执行开始产出的计划步骤快照（可见思考产物，不作为执行依据） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> plan;

    /** 预算快照 {maxSteps, maxDurationSeconds, maxTokens, maxCost}，按类型默认签发，只可下调 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> budget;

    /** 已执行步数（每次工具调用或决策计一步） */
    private Integer steps;

    private Integer tokensIn;

    private Integer tokensOut;

    /** 执行级成本汇总 */
    private BigDecimal cost;

    /** 执行时长（毫秒） */
    private Integer durationMs;

    /** 终止原因错误码（10 位；预算耗尽 1000018127、执行错误 1000018117） */
    private Integer terminateCode;

    private String terminateMsg;

    /** 再次执行时指向原执行（原执行与轨迹保留） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID retryOfExecutionId;

    /** 执行心跳，超时清扫器扫描依据 */
    private LocalDateTime heartbeatAt;
}
