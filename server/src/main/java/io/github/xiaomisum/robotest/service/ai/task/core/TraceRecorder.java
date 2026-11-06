package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTraceEvent;
import io.github.xiaomisum.robotest.repository.ai.AiTaskTraceEventMapper;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 轨迹事件记录（总册 2.6.3）：只增不改——本组件只提供 insert，
 * seq 按执行内 max+1 分配（uk_ai_task_trace_event 兜底并发），是审计与回放的唯一事实源。
 */
@Component
public class TraceRecorder {

    /** 事件类型全集（总册 2.6.3，非法值为装配缺陷） */
    static final List<String> EVENT_TYPES = List.of(
            Constants.AiTraceEventType.PLAN,
            Constants.AiTraceEventType.AI_DECISION,
            Constants.AiTraceEventType.TOOL_CALL,
            Constants.AiTraceEventType.TOOL_RESULT,
            Constants.AiTraceEventType.PROPOSAL_EMITTED,
            Constants.AiTraceEventType.STATUS_CHANGE);

    private static final int SUMMARY_MAX = 500;

    @Resource
    private AiTaskTraceEventMapper traceEventMapper;

    /**
     * 追加一条轨迹事件，返回分配的 seq。
     */
    public int record(UUID executionId, String eventType, String summary, Map<String, Object> detail) {
        if (!EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("非法轨迹事件类型: " + eventType);
        }
        int seq = nextSeq(executionId);
        AiTaskTraceEvent event = new AiTaskTraceEvent();
        event.setExecutionId(executionId);
        event.setSeq(seq);
        event.setEventType(eventType);
        event.setSummary(fitSummary(summary));
        event.setDetail(detail);
        traceEventMapper.insert(event);
        return seq;
    }

    /** 截断到列宽：超长时保留 499 字符 + 省略号，保证 ≤ 500（abbreviate 会在 limit 外再加省略号） */
    private static String fitSummary(String summary) {
        String value = ModelOutputSupport.nvl(summary);
        return value.length() <= SUMMARY_MAX ? value : value.substring(0, SUMMARY_MAX - 1) + "…";
    }

    private int nextSeq(UUID executionId) {
        AiTaskTraceEvent last = traceEventMapper.selectOne(new LambdaQueryWrapperX<AiTaskTraceEvent>()
                .eq(AiTaskTraceEvent::getExecutionId, executionId)
                .orderByDesc(AiTaskTraceEvent::getSeq)
                .last("LIMIT 1"));
        return last == null ? 1 : last.getSeq() + 1;
    }
}
