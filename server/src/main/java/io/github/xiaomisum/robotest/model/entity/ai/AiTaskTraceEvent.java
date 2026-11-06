package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

/**
 * 执行轨迹事件（详设 2.6.3）：只增不改，是执行过程审计与回放的唯一事实源，
 * 游标增量拉取按 (execution_id, seq) 单调递增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task_trace_event", autoResultMap = true)
public class AiTaskTraceEvent extends BaseUuidDO<AiTaskTraceEvent> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executionId;

    /** 执行内单调递增序号 */
    private Integer seq;

    /** plan / ai_decision / tool_call / tool_result / proposal_emitted / status_change（详设 2.6.3） */
    private String eventType;

    /** 事件摘要（面向用户展示） */
    private String summary;

    /** 事件明细（工具参数与结果摘要、决策上下文摘要等） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> detail;
}
