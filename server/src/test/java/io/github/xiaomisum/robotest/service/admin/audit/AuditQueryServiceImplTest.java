package io.github.xiaomisum.robotest.service.admin.audit;

import io.github.xiaomisum.robotest.model.dto.response.admin.AuditLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import io.github.xiaomisum.robotest.repository.admin.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceImplTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditQueryServiceImpl service;

    @Test
    void page_convertsEntityToRespDtoAndKeepsTotal() {
        AuditLog log = new AuditLog();
        log.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        log.setOperatorName("admin");
        log.setOperation("UPDATE");
        log.setEntityType("AiConfig");
        log.setEntityId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        log.setChanges(Map.of("name", "x"));
        log.setCreatedAt(LocalDateTime.of(2026, 9, 13, 10, 0));
        when(auditLogMapper.selectPageByCondition("admin", "AiConfig", null, null, 1, 20))
                .thenReturn(new PageResult<>(List.of(log), 12L));

        PageResult<AuditLogRespDTO> result = service.page("admin", "AiConfig", null, null, 1, 20);

        assertEquals(12L, result.getTotal());
        assertEquals(1, result.getList().size());
        AuditLogRespDTO dto = result.getList().get(0);
        assertEquals(log.getId(), dto.getId());
        assertEquals("AiConfig", dto.getEntityType());
        assertEquals(log.getCreatedAt(), dto.getCreatedAt());
    }

    @Test
    void page_withDateRangeConvertsToDayBoundaries() {
        when(auditLogMapper.selectPageByCondition("admin", null,
                LocalDate.of(2026, 9, 1).atStartOfDay(),
                LocalDate.of(2026, 9, 2).atTime(23, 59, 59), 1, 20))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.page("admin", null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1, 20);

        verify(auditLogMapper).selectPageByCondition("admin", null,
                LocalDate.of(2026, 9, 1).atStartOfDay(),
                LocalDate.of(2026, 9, 2).atTime(23, 59, 59), 1, 20);
    }

    @Test
    void aggregate_returnsOrderedDailyCounts() {
        Map<String, Object> day1 = new HashMap<>();
        day1.put("key", "2026-09-11");
        day1.put("calls", 20L);
        Map<String, Object> day2 = new HashMap<>();
        day2.put("key", "2026-09-12");
        day2.put("calls", 35L);
        when(auditLogMapper.aggregateByDay("AiConfig", LocalDate.of(2026, 9, 10).atStartOfDay()))
                .thenReturn(List.of(day1, day2));

        Map<String, Long> result = service.aggregate("AiConfig", LocalDate.of(2026, 9, 10));

        assertEquals(List.of("2026-09-11", "2026-09-12"), List.copyOf(result.keySet()));
        assertEquals(20L, result.get("2026-09-11"));
        assertEquals(35L, result.get("2026-09-12"));
    }
}