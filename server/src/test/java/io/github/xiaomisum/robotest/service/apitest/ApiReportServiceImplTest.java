package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.model.entity.project.ProjectSetting;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.project.ProjectSettingMapper;
import io.github.xiaomisum.robotest.service.project.ProjectSettingRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 报告分享与免登录访问核心路径（测试报告详细设计 4.2、基础设施详细设计 3.4） */
@ExtendWith(MockitoExtension.class)
class ApiReportServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();
    private static final int CODE_7007 = 1000017311;
    private static final int CODE_7008 = 1000017312;
    private static final int CODE_7009 = 1000017313;

    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ProjectSettingMapper projectSettingMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiReportServiceImpl service;

    private ApiReport sharedReport(LocalDateTime expiresAt) {
        ApiReport report = new ApiReport();
        report.setId(REPORT_ID);
        report.setProjectId(PROJECT_ID);
        report.setSceneName("登录链路");
        report.setStatus("success");
        report.setCreatedAt(LocalDateTime.now());
        report.setShareEnabled(true);
        report.setShareToken("a".repeat(32));
        report.setShareExpiresAt(expiresAt);
        report.setSummary(Map.of("total", 2, "passed", 2));
        report.setStepResults(List.of(Map.of("stepId", "s1", "name", "登录", "status", "success")));
        return report;
    }

    private void stubShareEnabled(boolean enabled) {
        ProjectSetting row = new ProjectSetting();
        row.setSettingValue(enabled ? "true" : "false");
        when(projectSettingMapper.findByProjectIdAndDomainAndKey(
                PROJECT_ID, ProjectSettingRegistry.Domain.API_TEST,
                ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED)).thenReturn(row);
        if (enabled) {
            ProjectSetting expireRow = new ProjectSetting();
            expireRow.setSettingValue("30");
            when(projectSettingMapper.findByProjectIdAndDomainAndKey(
                    PROJECT_ID, ProjectSettingRegistry.Domain.API_TEST,
                    ProjectSettingRegistry.Key.REPORT_SHARE_EXPIRE_DAYS)).thenReturn(expireRow);
        }
    }

    // ========== 分享 ==========

    @Test
    void shareRejectsWhenProjectSettingDisabled() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(null));
        stubShareEnabled(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.share(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID, null));
        assertEquals(CODE_7008, ex.getCode().intValue());
    }

    @Test
    void shareWritesTokenAndExpiryFromProjectSettings() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(null));
        stubShareEnabled(true);

        ApiReportShareRespDTO resp = service.share(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID, null);

        assertEquals("/share/api-report/" + REPORT_ID + "?token=" + resp.getShareUrl()
                .split("token=")[1], resp.getShareUrl());
        assertEquals(32, resp.getShareUrl().split("token=")[1].length());
        assertTrue(resp.getShareUrl().split("token=")[1].matches("[0-9a-f]{32}"));
        assertNotNull(resp.getExpiresAt());

        ArgumentCaptor<ApiReport> captor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(captor.capture());
        ApiReport carrier = captor.getValue();
        // 部分更新：只允许携带分享三列，防止整行覆盖并发变更
        assertEquals(Boolean.TRUE, carrier.getShareEnabled());
        assertEquals(32, carrier.getShareToken().length());
        assertTrue(carrier.getShareExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
        assertTrue(carrier.getShareExpiresAt().isBefore(LocalDateTime.now().plusDays(31)));
    }

    // ========== 免登录访问 ==========

    @Test
    void publicAccessRejectsInvalidTokenWith7009() {
        when(reportMapper.selectByIdAndToken(REPORT_ID, "bad")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.publicAccess(REPORT_ID, "bad"));
        assertEquals(CODE_7009, ex.getCode().intValue());
    }

    @Test
    void publicAccessRejectsExpiredTokenWith7009() {
        when(reportMapper.selectByIdAndToken(REPORT_ID, "a".repeat(32)))
                .thenReturn(sharedReport(LocalDateTime.now().minusMinutes(1)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.publicAccess(REPORT_ID, "a".repeat(32)));
        assertEquals(CODE_7009, ex.getCode().intValue());
    }

    @Test
    void publicAccessReturnsContentWithoutInternalSnapshot() {
        when(reportMapper.selectByIdAndToken(REPORT_ID, "a".repeat(32)))
                .thenReturn(sharedReport(LocalDateTime.now().plusDays(7)));

        ApiPublicReportRespDTO resp = service.publicAccess(REPORT_ID, "a".repeat(32));

        assertEquals("登录链路", resp.getSceneName());
        assertEquals(1, resp.getStepResults().size());
        assertFalse(resp.toString().contains("ryzeSnapshot"));
    }

    // ========== 查询与导出 ==========

    @Test
    void detailRejectsForeignOrMissingReportWith7007() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.detail(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID));
        assertEquals(CODE_7007, ex.getCode().intValue());
    }

    @Test
    void exportJsonContainsMetadataAndStepsButNoRyzeSnapshot() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(null));

        ApiReportService.ExportFile file = service.exportJson(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID);
        String json = new String(file.content(), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"sceneName\":\"登录链路\""));
        assertTrue(json.contains("\"stepResults\""));
        assertFalse(json.contains("ryzeSnapshot"));
        assertEquals("application/json", file.contentType());
    }

    @Test
    void batchExportZipProducesOneEntryPerReport() throws Exception {
        UUID secondId = UUID.randomUUID();
        ApiReport first = sharedReport(null);
        ApiReport second = sharedReport(null);
        second.setId(secondId);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(first);
        when(reportMapper.selectById(secondId)).thenReturn(second);

        ApiReportService.ExportFile zip = service.batchExportZip(
                WORKSPACE_ID, PROJECT_ID, USER_ID, List.of(REPORT_ID, secondId));

        assertEquals("application/zip", zip.contentType());
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip.content()))) {
            assertEquals("1-登录链路-" + LocalDateTime.now().toLocalDate() + ".json", zis.getNextEntry().getName());
            assertNotNull(zis.getNextEntry());
        }
    }

    @Test
    void deleteRejectsForeignReport() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.delete(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID));
        assertEquals(CODE_7007, ex.getCode().intValue());
        verify(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);
    }
}
