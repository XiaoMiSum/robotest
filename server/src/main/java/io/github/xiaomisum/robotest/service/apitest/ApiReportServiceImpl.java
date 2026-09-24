package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.time.UtcTime;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportPageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ApiReportServiceImpl implements ApiReportService {

    private static final String SHARE_URL_PREFIX = "/share/api-report/";
    /** expiresInDays 缺省值：生成时未指定时使用（对齐邀请链接的默认过期时间） */
    private static final int DEFAULT_SHARE_EXPIRE_DAYS = 7;

    @Resource
    private ApiReportMapper reportMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private SysUserMapper sysUserMapper;

    // ========== 查询 ==========

    @Override
    public PageResult<ApiReportPageItemRespDTO> page(UUID workspaceId, UUID projectId, UUID userId,
            PageParam pageParam, String status, String reportType, String executionMode, String keyword,
            LocalDateTime startDate, LocalDateTime endDate) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<ApiReport> pageResult = reportMapper.selectPageByProject(
                projectId, pageParam, status, reportType, executionMode, keyword, startDate, endDate);
        List<ApiReportPageItemRespDTO> items = pageResult.getList().stream().map(this::toPageItem).toList();
        return new PageResult<>(items, pageResult.getTotal());
    }

    private ApiReportPageItemRespDTO toPageItem(ApiReport report) {
        return ApiReportPageItemRespDTO.builder()
                .id(report.getId().toString())
                .reportType(report.getReportType())
                .externalId(report.getExternalId() == null ? null : report.getExternalId().toString())
                .name(report.getName())
                .sceneName(sceneNameOf(report))
                .executionMode(report.getExecutionMode())
                .status(report.getStatus())
                .summary(report.getSummary())
                .environmentName(report.getEnvironmentName())
                .createdAt(report.getCreatedAt())
                .build();
    }

    /** 场景报告取数据集内 sceneName 快照；套件报告列表不展示场景名 */
    private String sceneNameOf(ApiReport report) {
        if (report.getResult() == null) {
            return null;
        }
        Object sceneName = report.getResult().get("sceneName");
        return sceneName == null ? null : sceneName.toString();
    }

    @Override
    public ApiReportDetailRespDTO detail(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiReport report = requireReport(projectId, id);
        return ApiReportDetailRespDTO.builder()
                .id(report.getId().toString())
                .reportType(report.getReportType())
                .externalId(report.getExternalId() == null ? null : report.getExternalId().toString())
                .name(report.getName())
                .executionMode(report.getExecutionMode())
                .status(report.getStatus())
                .summary(report.getSummary())
                .environmentName(report.getEnvironmentName())
                .result(report.getResult())
                .share(shareOf(report))
                .createdAt(report.getCreatedAt())
                .build();
    }

    // ========== 分享 ==========

    @Override
    public ApiReportShareRespDTO share(UUID workspaceId, UUID projectId, UUID userId, UUID id,
            Integer expiresInDays) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireReport(projectId, id);

        // 无全局分享开关：有效期生成时选择，缺省 DEFAULT_SHARE_EXPIRE_DAYS（参照邀请链接）
        int days = expiresInDays != null ? expiresInDays : DEFAULT_SHARE_EXPIRE_DAYS;

        // UUID v4 去横线 = 32 位十六进制（测试报告详细设计 4.2.1）
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(days);

        // 部分更新：只写分享三列，防止整行覆盖并发变更
        ApiReport update = new ApiReport();
        update.setId(id);
        update.setShareToken(token);
        update.setShareExpiresAt(expiresAt);
        update.setShareUserId(userId);
        reportMapper.updateById(update);

        return ApiReportShareRespDTO.builder()
                .shareUrl(SHARE_URL_PREFIX + id + "?token=" + token)
                .expiresAt(UtcTime.toUtcWallClock(expiresAt))
                .shareBy(usernameOf(userId))
                .build();
    }

    /** 当前未过期分享记录（分享弹窗直接复用展示）；无分享/已过期返回 null */
    private ApiReportShareRespDTO shareOf(ApiReport report) {
        if (report.getShareToken() == null || report.getShareExpiresAt() == null
                || !report.getShareExpiresAt().isAfter(UtcTime.utcNow())) {
            return null;
        }
        return ApiReportShareRespDTO.builder()
                .shareUrl(SHARE_URL_PREFIX + report.getId() + "?token=" + report.getShareToken())
                .expiresAt(report.getShareExpiresAt())
                .shareBy(usernameOf(report.getShareUserId()))
                .build();
    }

    private String usernameOf(UUID userId) {
        if (userId == null) {
            return null;
        }
        List<SysUser> users = sysUserMapper.listByIds(List.of(userId));
        return users.isEmpty() ? null : users.get(0).getUsername();
    }

    @Override
    public ApiPublicReportRespDTO publicAccess(UUID id, String token) {
        ApiReport report = token == null || token.isBlank() ? null : reportMapper.selectByIdAndToken(id, token);
        // 未分享（无 token 行）/过期统一 7009，不区分具体原因避免枚举探测
        if (report == null || report.getShareExpiresAt() == null
                || !report.getShareExpiresAt().isAfter(UtcTime.utcNow())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SHARE_EXPIRED);
        }
        return ApiPublicReportRespDTO.builder()
                .id(report.getId().toString())
                .reportType(report.getReportType())
                .name(report.getName())
                .environmentName(report.getEnvironmentName())
                .status(report.getStatus())
                .summary(report.getSummary())
                .result(report.getResult())
                .createdAt(report.getCreatedAt())
                .build();
    }

    /** 报告校验：归属项目校验，避免越权访问其他项目报告 */
    private ApiReport requireReport(UUID projectId, UUID id) {
        ApiReport report = reportMapper.selectById(id);
        if (report == null || !report.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_REPORT_NOT_FOUND);
        }
        return report;
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

