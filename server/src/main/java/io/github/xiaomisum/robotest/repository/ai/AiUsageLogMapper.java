package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageSeriesRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageSummaryRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiUsageTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiUsageLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiUsageLogMapper extends BaseMapperX<AiUsageLog> {

    String RANGE_FILTER = "is_deleted = FALSE AND created_at >= #{from} AND created_at < #{to}";

    /** script 段内小于号须转义，否则 XML 解析失败（RANGE_FILTER 非 script 段保持原样） */
    String TASK_FILTER = "u.is_deleted = FALSE AND u.created_at >= #{from} AND u.created_at &lt; #{to}";

    /** 区间汇总（详设 3.7 summary）：UTC 分组，失败 = status = failed */
    @Select("SELECT COUNT(*) AS total_calls, "
            + "COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed_calls, "
            + "COALESCE(SUM(total_tokens), 0) AS total_tokens, "
            + "COALESCE(AVG(latency_ms), 0) AS avg_latency_ms, "
            + "COALESCE(SUM(cost), 0) AS total_cost "
            + "FROM ai_usage_log WHERE " + RANGE_FILTER)
    AiUsageSummaryRowDTO selectSummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 按 UTC 日期分组（created_at 即 UTC，直接截日） */
    @Select("SELECT to_char(created_at, 'YYYY-MM-DD') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(AVG(latency_ms), 0) AS avg_latency_ms, "
            + "COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_usage_log WHERE " + RANGE_FILTER + " GROUP BY 1 ORDER BY 1")
    List<AiUsageSeriesRowDTO> selectSeriesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT model_id::text AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(AVG(latency_ms), 0) AS avg_latency_ms, "
            + "COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_usage_log WHERE " + RANGE_FILTER + " GROUP BY model_id ORDER BY 1")
    List<AiUsageSeriesRowDTO> selectSeriesByModel(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT COALESCE(prompt_scene, '') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(AVG(latency_ms), 0) AS avg_latency_ms, "
            + "COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_usage_log WHERE " + RANGE_FILTER + " GROUP BY prompt_scene ORDER BY 1")
    List<AiUsageSeriesRowDTO> selectSeriesByScene(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT call_type AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0) AS failed, "
            + "COALESCE(SUM(total_tokens), 0) AS tokens, COALESCE(AVG(latency_ms), 0) AS avg_latency_ms, "
            + "COALESCE(SUM(cost), 0) AS cost "
            + "FROM ai_usage_log WHERE " + RANGE_FILTER + " GROUP BY call_type ORDER BY 1")
    List<AiUsageSeriesRowDTO> selectSeriesByCallType(@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** 下钻总数（动态筛选：modelId / scene / callType / status） */
    @Select("<script>SELECT COUNT(*) FROM ai_usage_log u WHERE " + TASK_FILTER
            + "<if test='modelId != null'> AND u.model_id = #{modelId}</if>"
            + "<if test=\"scene != null and scene != ''\"> AND u.prompt_scene = #{scene}</if>"
            + "<if test=\"callType != null and callType != ''\"> AND u.call_type = #{callType}</if>"
            + "<if test=\"status != null and status != ''\"> AND u.status = #{status}</if>"
            + "</script>")
    long countUsageTasks(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("modelId") UUID modelId, @Param("scene") String scene,
            @Param("callType") String callType, @Param("status") String status);

    /** 下钻分页：join 任务类型与模型名（详设 3.7 列表项字段） */
    @Select("<script>SELECT u.task_id AS taskId, t.type AS type, u.status AS status, "
            + "m.name AS modelName, u.prompt_scene AS scene, u.total_tokens AS totalTokens, "
            + "u.latency_ms AS latencyMs, u.created_at AS createdAt "
            + "FROM ai_usage_log u "
            + "LEFT JOIN ai_task t ON t.id = u.task_id AND t.is_deleted = FALSE "
            + "LEFT JOIN ai_model_config m ON m.id = u.model_id AND m.is_deleted = FALSE "
            + "WHERE " + TASK_FILTER
            + "<if test='modelId != null'> AND u.model_id = #{modelId}</if>"
            + "<if test=\"scene != null and scene != ''\"> AND u.prompt_scene = #{scene}</if>"
            + "<if test=\"callType != null and callType != ''\"> AND u.call_type = #{callType}</if>"
            + "<if test=\"status != null and status != ''\"> AND u.status = #{status}</if>"
            + " ORDER BY u.created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<AiUsageTaskRespDTO> pageUsageTasks(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("modelId") UUID modelId, @Param("scene") String scene,
            @Param("callType") String callType, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") long offset);
}
