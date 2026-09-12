package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiPublicReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiReportShareRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final int CODE_7009 = 1000017312;

    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private ApiReportServiceImpl service;

    private void mockShareUserName() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("tester");
        when(sysUserMapper.listByIds(any())).thenReturn(List.of(user));
    }

    private ApiReport sharedReport(LocalDateTime expiresAt) {
        ApiReport report = new ApiReport();
        report.setId(REPORT_ID);
        report.setProjectId(PROJECT_ID);
        report.setReportType("scene");
        report.setName("登录链路");
        report.setStatus("success");
        report.setCreatedAt(LocalDateTime.now());
        report.setShareToken("a".repeat(32));
        report.setShareExpiresAt(expiresAt);
        report.setShareUserId(USER_ID);
        report.setSummary(Map.of("total", 2, "passed", 2));
        report.setResult(Map.of("sceneId", "scene-1", "sceneName", "登录链路", "status", "success",
                "summary", Map.of("total", 2, "passed", 2),
                "steps", List.of(Map.of("stepId", "s1", "name", "登录", "status", "success"))));
        return report;
    }

    // ========== 分享 ==========

    @Test
    void shareDefaultsExpiryTo7DaysWhenNotSpecified() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(null));
        mockShareUserName();

        ApiReportShareRespDTO resp = service.share(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID, null);

        assertEquals(32, resp.getShareUrl().split("token=")[1].length());
        assertTrue(resp.getShareUrl().split("token=")[1].matches("[0-9a-f]{32}"));
        assertNotNull(resp.getExpiresAt());
        // 分享者随分享记录返回，供复制文本展示
        assertEquals("tester", resp.getShareBy());

        ArgumentCaptor<ApiReport> captor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(captor.capture());
        ApiReport carrier = captor.getValue();
        // 部分更新：只携带分享列，防止整行覆盖并发变更
        assertEquals(32, carrier.getShareToken().length());
        assertTrue(carrier.getShareExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
        assertTrue(carrier.getShareExpiresAt().isBefore(LocalDateTime.now().plusDays(8)));
        assertEquals(USER_ID, carrier.getShareUserId());
    }

    @Test
    void shareUsesExplicitExpiryDays() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(null));
        mockShareUserName();

        ApiReportShareRespDTO resp = service.share(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID, 30);

        ArgumentCaptor<ApiReport> captor = ArgumentCaptor.forClass(ApiReport.class);
        verify(reportMapper).updateById(captor.capture());
        ApiReport carrier = captor.getValue();
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

        assertEquals("登录链路", resp.getName());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.getResult();
        assertEquals("scene", resp.getReportType());
        assertEquals(1, ((List<?>) result.get("steps")).size());
        assertFalse(resp.toString().contains("ryzeSnapshot"));
    }

    // ========== 查询与删除 ==========

    @Test
    void detailRejectsForeignOrMissingReportWith7007() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.detail(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID));
        assertEquals(CODE_7007, ex.getCode().intValue());
    }

    @Test
    void detailReturnsShareOnlyWhenUnexpired() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(LocalDateTime.now().plusDays(7)));
        mockShareUserName();

        ApiReportDetailRespDTO resp = service.detail(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID);

        assertNotNull(resp.getShare());
        assertTrue(resp.getShare().getShareUrl().contains(reportShareToken()));
        assertEquals("tester", resp.getShare().getShareBy());
    }

    @Test
    void detailOmitsExpiredOrMissingShare() {
        when(reportMapper.selectById(REPORT_ID)).thenReturn(sharedReport(LocalDateTime.now().minusMinutes(1)));

        ApiReportDetailRespDTO expired = service.detail(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID);
        assertNull(expired.getShare());

        ApiReport none = sharedReport(null);
        none.setShareToken(null);
        none.setShareExpiresAt(null);
        when(reportMapper.selectById(REPORT_ID)).thenReturn(none);
        ApiReportDetailRespDTO missing = service.detail(WORKSPACE_ID, PROJECT_ID, USER_ID, REPORT_ID);
        assertNull(missing.getShare());
    }

    /** 与 sharedReport() 中固化的 token 保持一致 */
    private String reportShareToken() {
        return "a".repeat(32);
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
