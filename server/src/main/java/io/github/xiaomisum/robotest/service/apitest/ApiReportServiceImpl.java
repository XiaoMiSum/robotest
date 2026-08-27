package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportPageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.project.ProjectSetting;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.project.ProjectSettingMapper;
import io.github.xiaomisum.robotest.service.project.ProjectSettingRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ApiReportServiceImpl implements ApiReportService {

    private static final String SHARE_URL_PREFIX = "/share/api-report/";

    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private ProjectSettingMapper projectSettingMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    // ========== 查询 ==========

    @Override
    public PageResult<ApiReportPageItemRespDTO> page(UUID workspaceId, UUID projectId, UUID userId,
            PageParam pageParam, String status, UUID sceneId, String executionMode, String keyword,
            LocalDateTime startDate, LocalDateTime endDate) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<ApiReport> pageResult = reportMapper.selectPageByProject(
                projectId, pageParam, status, sceneId, executionMode, keyword, startDate, endDate);
        List<ApiReportPageItemRespDTO> items = pageResult.getList().stream().map(this::toPageItem).toList();
        return new PageResult<>(items, pageResult.getTotal());
    }

    private ApiReportPageItemRespDTO toPageItem(ApiReport report) {
        return ApiReportPageItemRespDTO.builder()
                .id(report.getId().toString())
                .sceneId(report.getSceneId() == null ? null : report.getSceneId().toString())
                .sceneName(report.getSceneName())
                .executionMode(report.getExecutionMode())
                .status(report.getStatus())
                .summary(report.getSummary())
                .environmentName(report.getEnvironmentName())
                .createdAt(report.getCreatedAt())
                .build();
    }

    @Override
    public ApiReportDetailRespDTO detail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiReport report = requireReport(projectId, id);
        return ApiReportDetailRespDTO.builder()
                .id(report.getId().toString())
                .sceneId(report.getSceneId() == null ? null : report.getSceneId().toString())
                .sceneName(report.getSceneName())
                .executionMode(report.getExecutionMode())
                .status(report.getStatus())
                .summary(report.getSummary())
                .environmentName(report.getEnvironmentName())
                .stepResults(report.getStepResults())
                .shareEnabled(Boolean.TRUE.equals(report.getShareEnabled()))
                .createdAt(report.getCreatedAt())
                .build();
    }

    // ========== 分享 ==========

    @Override
    public ApiReportShareRespDTO share(UUID workspaceId, UUID projectId, UUID userId, UUID id,
            Integer expiresInDays) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireReport(projectId, id);

        if (!"true".equals(readSetting(projectId, ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SHARE_NOT_ENABLED);
        }
        int days = expiresInDays != null ? expiresInDays
                : Integer.parseInt(readSetting(projectId, ProjectSettingRegistry.Key.REPORT_SHARE_EXPIRE_DAYS));

        // UUID v4 去横线 = 32 位十六进制（测试报告详细设计 4.2.1）
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(days);

        // 部分更新：只写分享三列
        ApiReport update = new ApiReport();
        update.setId(id);
        update.setShareEnabled(true);
        update.setShareToken(token);
        update.setShareExpiresAt(expiresAt);
        reportMapper.updateById(update);

        return ApiReportShareRespDTO.builder()
                .shareUrl(SHARE_URL_PREFIX + id + "?token=" + token)
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public ApiPublicReportRespDTO publicAccess(UUID id, String token) {
        ApiReport report = token == null || token.isBlank() ? null : reportMapper.selectByIdAndToken(id, token);
        // 无效/未开启/过期统一 7009，不区分具体原因避免枚举探测
        if (report == null || !Boolean.TRUE.equals(report.getShareEnabled())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SHARE_EXPIRED);
        }
        if (report.getShareExpiresAt() == null || !report.getShareExpiresAt().isAfter(LocalDateTime.now())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SHARE_EXPIRED);
        }
        return ApiPublicReportRespDTO.builder()
                .id(report.getId().toString())
                .sceneName(report.getSceneName())
                .environmentName(report.getEnvironmentName())
                .status(report.getStatus())
                .summary(report.getSummary())
                .stepResults(report.getStepResults())
                .createdAt(report.getCreatedAt())
                .build();
    }

    /** 项目设置读取：未落库键回退注册表默认值（与 ProjectSettingServiceImpl 口径一致） */
    private String readSetting(UUID projectId, String settingKey) {
        ProjectSettingRegistry.SettingDefinition definition =
                ProjectSettingRegistry.find(ProjectSettingRegistry.Domain.API_TEST, settingKey);
        ProjectSetting row = projectSettingMapper.findByProjectIdAndDomainAndKey(
                projectId, ProjectSettingRegistry.Domain.API_TEST, settingKey);
        return row != null ? row.getSettingValue() : definition.defaultValue();
    }

    private ApiReport requireReport(UUID projectId, UUID id) {
        ApiReport report = reportMapper.selectById(id);
        if (report == null || !report.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_REPORT_NOT_FOUND);
        }
        return report;
    }

    // ========== 导出 ==========

    @Override
    public ExportFile exportJson(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiReport report = requireReport(projectId, id);
        byte[] content = JsonUtils.toJsonString(toExportMap(report)).getBytes(StandardCharsets.UTF_8);
        return new ExportFile(exportFilename(report, "json"), "application/json", content);
    }

    @Override
    public ExportFile exportHtml(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiReport report = requireReport(projectId, id);
        String html = HtmlReportRenderer.render(report.getSceneName(), report.getStatus(),
                report.getEnvironmentName(), report.getExecutionMode(), report.getCreatedAt(),
                report.getSummary(), report.getStepResults());
        return new ExportFile(exportFilename(report, "html"), "text/html", html.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ExportFile batchExportZip(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<ApiReport> reports = ids.stream().map(id -> requireReport(projectId, id)).toList();
        try (var baos = new java.io.ByteArrayOutputStream();
                var zip = new ZipOutputStream(baos)) {
            for (int i = 0; i < reports.size(); i++) {
                ApiReport report = reports.get(i);
                // 前缀序号防重名：同场景多次执行会派生同名文件
                zip.putNextEntry(new ZipEntry((i + 1) + "-" + exportFilename(report, "json")));
                zip.write(JsonUtils.toJsonString(toExportMap(report)).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return new ExportFile("api-reports-" + System.currentTimeMillis() + ".zip", "application/zip", baos.toByteArray());
        } catch (Exception ex) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FORMAT_CONVERT_FAILED, ex.getMessage());
        }
    }

    /** 导出内容：元数据 + 汇总 + 步骤级明细（测试报告详细设计 4.3.1），不含 ryze_snapshot */
    private Map<String, Object> toExportMap(ApiReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sceneName", report.getSceneName());
        map.put("environmentName", report.getEnvironmentName());
        map.put("executionMode", report.getExecutionMode());
        map.put("status", report.getStatus());
        map.put("createdAt", report.getCreatedAt());
        map.put("summary", report.getSummary());
        map.put("stepResults", report.getStepResults());
        return map;
    }

    private String exportFilename(ApiReport report, String extension) {
        String sceneName = report.getSceneName() == null ? "report" : report.getSceneName();
        // 文件名只保留安全字符，避免路径穿越与非法字符
        String safe = sceneName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String stamp = report.getCreatedAt() == null ? Long.toString(System.currentTimeMillis())
                : report.getCreatedAt().toLocalDate().toString();
        return safe + "-" + stamp + "." + extension;
    }

    // ========== 删除 ==========

    @Override
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireReport(projectId, id);
        reportMapper.deleteById(id);
    }

    @Override
    public void batchDelete(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        // 逐条校验归属，避免越权 ID 混入批量操作
        for (UUID id : ids) {
            requireReport(projectId, id);
            reportMapper.deleteById(id);
        }
    }
}

