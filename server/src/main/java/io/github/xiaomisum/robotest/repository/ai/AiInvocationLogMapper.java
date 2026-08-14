package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiInvocationLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import xyz.migoo.framework.mybatis.core.BaseMapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AiInvocationLogMapper extends BaseMapperX<AiInvocationLog> {

    // ========== 保留期两阶段清理（先逻辑删除、次日物理删除，避免误删无法恢复） ==========

    @Update("UPDATE ai_invocation_log SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP "
            + "WHERE is_deleted = FALSE AND created_at < #{before}")
    int markExpired(@Param("before") LocalDateTime before);

    @Delete("DELETE FROM ai_invocation_log WHERE is_deleted = TRUE AND updated_at < #{before}")
    int purgeMarked(@Param("before") LocalDateTime before);

    // ========== 调用量统计聚合（管理端 3.3.4） ==========

    @Select("SELECT COUNT(*) AS calls, COALESCE(SUM(COALESCE(prompt_tokens,0) + COALESCE(completion_tokens,0)),0) AS tokens, "
            + "COUNT(*) FILTER (WHERE status <> 'success') AS failed "
            + "FROM ai_invocation_log WHERE is_deleted = FALSE AND created_at >= #{start} AND created_at < #{end}")
    Map<String, Object> aggregateTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT function_type AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(COALESCE(prompt_tokens,0) + COALESCE(completion_tokens,0)),0) AS tokens, "
            + "COALESCE(AVG(duration_ms),0) AS avg_duration_ms, COUNT(*) FILTER (WHERE status <> 'success') AS failed "
            + "FROM ai_invocation_log WHERE is_deleted = FALSE AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY function_type ORDER BY calls DESC")
    List<Map<String, Object>> aggregateByFunctionType(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(w.name, '-') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(COALESCE(l.prompt_tokens,0) + COALESCE(l.completion_tokens,0)),0) AS tokens, "
            + "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, COUNT(*) FILTER (WHERE l.status <> 'success') AS failed "
            + "FROM ai_invocation_log l LEFT JOIN ws_workspace w ON w.id = l.workspace_id "
            + "WHERE l.is_deleted = FALSE AND l.created_at >= #{start} AND l.created_at < #{end} "
            + "GROUP BY w.name ORDER BY calls DESC")
    List<Map<String, Object>> aggregateByWorkspace(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(COALESCE(prompt_tokens,0) + COALESCE(completion_tokens,0)),0) AS tokens, "
            + "COALESCE(AVG(duration_ms),0) AS avg_duration_ms, COUNT(*) FILTER (WHERE status <> 'success') AS failed "
            + "FROM ai_invocation_log WHERE is_deleted = FALSE AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD') ORDER BY key")
    List<Map<String, Object>> aggregateByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(model, '-') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(COALESCE(prompt_tokens,0) + COALESCE(completion_tokens,0)),0) AS tokens, "
            + "COALESCE(AVG(duration_ms),0) AS avg_duration_ms, COUNT(*) FILTER (WHERE status <> 'success') AS failed "
            + "FROM ai_invocation_log WHERE is_deleted = FALSE AND created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY model ORDER BY calls DESC")
    List<Map<String, Object>> aggregateByModel(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COALESCE(u.name, u.username, '-') AS key, COUNT(*) AS calls, "
            + "COALESCE(SUM(COALESCE(l.prompt_tokens,0) + COALESCE(l.completion_tokens,0)),0) AS tokens, "
            + "COALESCE(AVG(l.duration_ms),0) AS avg_duration_ms, COUNT(*) FILTER (WHERE l.status <> 'success') AS failed "
            + "FROM ai_invocation_log l LEFT JOIN sys_user u ON u.id = l.user_id "
            + "WHERE l.is_deleted = FALSE AND l.created_at >= #{start} AND l.created_at < #{end} "
            + "GROUP BY u.name, u.username ORDER BY calls DESC")
    List<Map<String, Object>> aggregateByUser(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
