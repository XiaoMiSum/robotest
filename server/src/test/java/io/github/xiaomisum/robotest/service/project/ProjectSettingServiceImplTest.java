package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.project.ProjectSettingUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.project.ProjectSettingListRespDTO;
import io.github.xiaomisum.robotest.model.entity.project.ProjectSetting;
import io.github.xiaomisum.robotest.repository.project.ProjectSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSettingServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ROW_ID = UUID.randomUUID();

    @Mock
    private ProjectSettingMapper projectSettingMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ProjectSettingServiceImpl service;

    private static ProjectSettingUpdateReqDTO.Item item(String domain, String key, String value) {
        ProjectSettingUpdateReqDTO.Item item = new ProjectSettingUpdateReqDTO.Item();
        item.setDomain(domain);
        item.setSettingKey(key);
        item.setSettingValue(value);
        return item;
    }

    private static ProjectSettingUpdateReqDTO req(ProjectSettingUpdateReqDTO.Item... items) {
        ProjectSettingUpdateReqDTO dto = new ProjectSettingUpdateReqDTO();
        dto.setItems(List.of(items));
        return dto;
    }

    // ==================== 查询 ====================

    @Test
    void getSettings_mergesDefaultsAndExplicitRows() {
        when(projectSettingMapper.listByProjectIdAndDomain(PROJECT_ID, "api_test"))
                .thenReturn(List.of(row("report.share.expire-days", "30")));

        ProjectSettingListRespDTO resp = service.getSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, "api_test");

        assertEquals(2, resp.getItems().size());
        ProjectSettingListRespDTO.Item enabled = resp.getItems().stream()
                .filter(i -> "report.share.enabled".equals(i.getSettingKey())).findFirst().orElseThrow();
        assertFalse(enabled.getExplicit());
        assertEquals("false", enabled.getSettingValue());
        assertEquals("false", enabled.getDefaultValue());

        ProjectSettingListRespDTO.Item expireDays = resp.getItems().stream()
                .filter(i -> "report.share.expire-days".equals(i.getSettingKey())).findFirst().orElseThrow();
        assertTrue(expireDays.getExplicit());
        assertEquals("30", expireDays.getSettingValue());
        assertEquals("7", expireDays.getDefaultValue());
    }

    @Test
    void getSettings_commonDomain_returnsEmptyItems() {
        assertTrue(service.getSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, "common").getItems().isEmpty());
    }

    @Test
    void getSettings_unsupportedDomain_throwsValidationFailed() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, "func_test"));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), ex.getCode());
        verifyNoInteractions(projectAccessGuard);
    }

    // ==================== 更新：权限与整批校验 ====================

    @Test
    void updateSettings_notMember_throwsNoPermission() {
        doThrow(new ServiceException(ErrorCodeConstants.NO_PERMISSION.code(), "无权限执行此操作"))
                .when(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);

        assertThrows(ServiceException.class, () -> service.updateSettings(PROJECT_ID, WORKSPACE_ID, USER_ID,
                req(item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED, "true"))));
        verify(projectSettingMapper, never()).insert(any(ProjectSetting.class));
        verify(projectSettingMapper, never()).updateById(any(ProjectSetting.class));
    }

    @Test
    void updateSettings_unknownKey_rejectsWholeBatch() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, req(
                        item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED, "true"),
                        item("api_test", "unknown.key", "1"))));
        assertEquals(ErrorCodeConstants.API_SETTING_KEY_INVALID.code(), ex.getCode());
        verify(projectSettingMapper, never()).insert(any(ProjectSetting.class));
        verify(projectSettingMapper, never()).updateById(any(ProjectSetting.class));
    }

    @Test
    void updateSettings_invalidValue_rejectsWholeBatchWithKeyName() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, req(
                        item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED, "true"),
                        item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_EXPIRE_DAYS, "15"))));
        assertEquals(ErrorCodeConstants.API_SETTING_VALUE_INVALID.code(), ex.getCode());
        assertTrue(ex.getMessage().contains(ProjectSettingRegistry.Key.REPORT_SHARE_EXPIRE_DAYS));
        verify(projectSettingMapper, never()).insert(any(ProjectSetting.class));
        verify(projectSettingMapper, never()).updateById(any(ProjectSetting.class));
    }

    // ==================== 更新：upsert 写入 ====================

    @Test
    void updateSettings_insertsNewKeysAndReturnsCount() {
        when(projectSettingMapper.findByProjectIdAndDomainAndKey(any(), any(), any())).thenReturn(null);

        int updated = service.updateSettings(PROJECT_ID, WORKSPACE_ID, USER_ID, req(
                item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED, "true"),
                item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_EXPIRE_DAYS, "30")));

        assertEquals(2, updated);
        ArgumentCaptor<ProjectSetting> captor = ArgumentCaptor.forClass(ProjectSetting.class);
        verify(projectSettingMapper, times(2)).insert(captor.capture());
        List<ProjectSetting> inserted = captor.getAllValues();
        assertEquals(2, inserted.size());
        inserted.forEach(row -> {
            assertEquals(PROJECT_ID, row.getProjectId());
            assertEquals(USER_ID, row.getUpdatedBy());
            assertNull(row.getId());
        });
        assertTrue(inserted.stream().anyMatch(r ->
                "report.share.enabled".equals(r.getSettingKey()) && "true".equals(r.getSettingValue())));
        assertTrue(inserted.stream().anyMatch(r ->
                "report.share.expire-days".equals(r.getSettingKey()) && "30".equals(r.getSettingValue())));
        verify(projectSettingMapper, never()).updateById(any(ProjectSetting.class));
    }

    @Test
    void updateSettings_updatesExistingCarrierOnly() {
        when(projectSettingMapper.findByProjectIdAndDomainAndKey(PROJECT_ID, "api_test",
                ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED))
                .thenReturn(row("report.share.enabled", "false"));

        int updated = service.updateSettings(PROJECT_ID, WORKSPACE_ID, USER_ID,
                req(item("api_test", ProjectSettingRegistry.Key.REPORT_SHARE_ENABLED, "true")));

        assertEquals(1, updated);
        ArgumentCaptor<ProjectSetting> captor = ArgumentCaptor.forClass(ProjectSetting.class);
        verify(projectSettingMapper).updateById(captor.capture());
        ProjectSetting carrier = captor.getValue();
        assertEquals(ROW_ID, carrier.getId());
        assertEquals("true", carrier.getSettingValue());
        assertEquals(USER_ID, carrier.getUpdatedBy());
        assertNull(carrier.getProjectId());
        assertNull(carrier.getDomain());
        assertNull(carrier.getSettingKey());
        verify(projectSettingMapper, never()).insert(any(ProjectSetting.class));
    }

    private ProjectSetting row(String key, String value) {
        ProjectSetting row = new ProjectSetting();
        row.setId(ROW_ID);
        row.setProjectId(PROJECT_ID);
        row.setDomain("api_test");
        row.setSettingKey(key);
        row.setSettingValue(value);
        row.setUpdatedBy(UUID.randomUUID());
        return row;
    }
}
