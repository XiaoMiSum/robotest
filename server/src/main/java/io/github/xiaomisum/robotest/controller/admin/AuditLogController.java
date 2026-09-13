package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.response.admin.AuditLogRespDTO;
import io.github.xiaomisum.robotest.service.admin.audit.AuditQueryService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    @Resource
    private AuditQueryService auditQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public Result<PageResult<AuditLogRespDTO>> getAuditLogPage(
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(auditQueryService.page(operatorName, entityType, beginTime, endTime, pageNo, pageSize));
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasAuthority('audit:view')")
    public Result<Map<String, Long>> aggregate(
            @RequestParam String entityType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return Result.ok(auditQueryService.aggregate(entityType, from));
    }
}