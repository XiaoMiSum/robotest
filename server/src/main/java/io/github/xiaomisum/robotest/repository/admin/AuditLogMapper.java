package io.github.xiaomisum.robotest.repository.admin;

import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapperX<AuditLog> {

    default PageResult<AuditLog> selectPageByCondition(String operatorName, String entityType, String operation,
                                                       LocalDateTime beginTime, LocalDateTime endTime,
                                                       Integer pageNo, Integer pageSize) {
        LambdaQueryWrapperX<AuditLog> wrapper = new LambdaQueryWrapperX<AuditLog>()
                .likeIfPresent(AuditLog::getOperatorName, operatorName)
                .eqIfPresent(AuditLog::getEntityType, entityType)
                .eqIfPresent(AuditLog::getOperation, operation)
                .geIfPresent(AuditLog::getCreatedAt, beginTime)
                .leIfPresent(AuditLog::getCreatedAt, endTime)
                .orderByDesc(AuditLog::getCreatedAt);
        return selectPage(new PageParam() {{
            setPageNo(pageNo);
            setPageSize(pageSize);
        }}, wrapper);
    }

    @Select("""
            SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS key, COUNT(*) AS calls
            FROM sys_audit_log
            WHERE is_deleted = FALSE AND entity_type = #{entityType} AND created_at >= #{from}
            GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
            ORDER BY key
            """)
    List<Map<String, Object>> aggregateByDay(@org.apache.ibatis.annotations.Param("entityType") String entityType,
                                             @org.apache.ibatis.annotations.Param("from") LocalDateTime from);

    /** 指定时间窗内的登录人次（数据概览「今日活跃」） */
    default long countLoginsBetween(LocalDateTime begin, LocalDateTime end) {
        LambdaQueryWrapperX<AuditLog> wrapper = new LambdaQueryWrapperX<AuditLog>()
                .eq(AuditLog::getOperation, "LOGIN")
                .ge(AuditLog::getCreatedAt, begin)
                .le(AuditLog::getCreatedAt, end);
        Long cnt = selectCount(wrapper);
        return cnt == null ? 0 : cnt;
    }

    /** 自 from 起按日去重的登录用户数（数据概览 14 日趋势，key=日期, users=人数） */
    @Select("""
            SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS key, COUNT(DISTINCT operator_id) AS users
            FROM sys_audit_log
            WHERE is_deleted = FALSE AND operation = 'LOGIN' AND created_at >= #{from}
            GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
            ORDER BY key
            """)
    List<Map<String, Object>> countDistinctLoginsByDay(@org.apache.ibatis.annotations.Param("from") LocalDateTime from);
}