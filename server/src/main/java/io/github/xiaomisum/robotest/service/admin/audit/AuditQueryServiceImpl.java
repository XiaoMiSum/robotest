package io.github.xiaomisum.robotest.service.admin.audit;

import io.github.xiaomisum.robotest.model.dto.response.admin.AuditLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogRespDTO> page(String operatorName, String entityType, String operation,
                                            LocalDate beginTime, LocalDate endTime,
                                            Integer pageNo, Integer pageSize) {
        PageResult<AuditLog> result = auditLogMapper.selectPageByCondition(
                operatorName, entityType, operation,
                toStartOfDay(beginTime), toEndOfDay(endTime),
                pageNo, pageSize);
        List<AuditLogRespDTO> list = result.getList().stream().map(AuditQueryServiceImpl::toRespDTO).toList();
        return new PageResult<>(list, result.getTotal());
    }

    @Override
    public Map<String, Long> aggregate(String entityType, LocalDate from) {
        List<Map<String, Object>> rows = auditLogMapper.aggregateByDay(entityType, toStartOfDay(from));
        // 按日有序：保证聚合结果日期升序展示
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("key")), ((Number) row.get("calls")).longValue());
        }
        return counts;
    }

    private static LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime toEndOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59);
    }

    private static AuditLogRespDTO toRespDTO(AuditLog log) {
        AuditLogRespDTO dto = new AuditLogRespDTO();
        dto.setId(log.getId());
        dto.setOperatorId(log.getOperatorId());
        dto.setOperatorName(log.getOperatorName());
        dto.setOperation(log.getOperation());
        dto.setEntityType(log.getEntityType());
        dto.setEntityId(log.getEntityId());
        dto.setChanges(log.getChanges());
        dto.setRequestIp(log.getRequestIp());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}