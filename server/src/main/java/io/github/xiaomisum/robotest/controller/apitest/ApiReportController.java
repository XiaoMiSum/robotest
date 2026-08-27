package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiReportBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiReportShareReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportPageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiReportService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/** 测试报告路由（基础设施详细设计 3.4 + 测试报告详细设计 3.1-3.3） */
@RestController
public class ApiReportController {

    @Resource
    private ApiReportService reportService;

    @GetMapping("/api/project/reports")
    @PreAuthorize("hasAuthority('api-report:view')")
    public Result<PageResult<ApiReportPageItemRespDTO>> page(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @Valid PageParam pageParam,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sceneId", required = false) UUID sceneId,
            @RequestParam(value = "executionMode", required = false) String executionMode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return Result.ok(reportService.page(workspaceId, projectId, loginUser.getId(), pageParam,
                status, sceneId, executionMode, keyword, startDate, endDate));
    }

    @GetMapping("/api/project/reports/{id}")
    @PreAuthorize("hasAuthority('api-report:view')")
    public Result<ApiReportDetailRespDTO> detail(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(reportService.detail(workspaceId, projectId, loginUser.getId(), id));
    }

    @PostMapping("/api/project/reports/{id}/share")
    @PreAuthorize("hasAuthority('api-report:view')")
    public Result<ApiReportShareRespDTO> share(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ApiReportShareReqDTO reqDTO) {
        return Result.ok(reportService.share(workspaceId, projectId, loginUser.getId(), id,
                reqDTO == null ? null : reqDTO.getExpiresInDays()));
    }

    @GetMapping("/api/project/reports/{id}/export")
    @PreAuthorize("hasAuthority('api-report:view')")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id,
            @RequestParam(value = "format", defaultValue = "json") String format) {
        ApiReportService.ExportFile file = "html".equalsIgnoreCase(format)
                ? reportService.exportHtml(workspaceId, projectId, loginUser.getId(), id)
                : reportService.exportJson(workspaceId, projectId, loginUser.getId(), id);
        return fileResponse(file);
    }

    @PostMapping("/api/project/reports/batch-export")
    @PreAuthorize("hasAuthority('api-report:view')")
    public ResponseEntity<byte[]> batchExport(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiReportBatchReqDTO reqDTO) {
        return fileResponse(reportService.batchExportZip(workspaceId, projectId, loginUser.getId(), reqDTO.getIds()));
    }

    @DeleteMapping("/api/project/reports/{id}")
    @PreAuthorize("hasAuthority('api-report:delete')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        reportService.delete(workspaceId, projectId, loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/api/project/reports/batch-delete")
    @PreAuthorize("hasAuthority('api-report:delete')")
    public Result<Boolean> batchDelete(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Workspace") UUID workspaceId,
            @RequestHeader("X-Active-Project") UUID projectId,
            @RequestBody @Valid ApiReportBatchReqDTO reqDTO) {
        reportService.batchDelete(workspaceId, projectId, loginUser.getId(), reqDTO.getIds());
        return Result.ok(true);
    }

    private ResponseEntity<byte[]> fileResponse(ApiReportService.ExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(file.content());
    }

}
