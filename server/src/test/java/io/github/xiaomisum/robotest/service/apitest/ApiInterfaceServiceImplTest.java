package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceVariablesReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportMapping;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceChangeLog;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceFollow;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportMappingMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceChangeLogMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceVariableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 接口定义管理服务：乐观锁、名称唯一、删除保护、视图分页、变量全量覆盖、导入 upsert */
@ExtendWith(MockitoExtension.class)
class ApiInterfaceServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INTERFACE_ID = UUID.randomUUID();

    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ApiInterfaceStepMapper stepMapper;
    @Mock
    private ApiInterfaceVariableMapper variableMapper;
    @Mock
    private ApiInterfaceFollowMapper followMapper;
    @Mock
    private ApiInterfaceChangeLogMapper changeLogMapper;
    @Mock
    private ApiImportMappingMapper importMappingMapper;
    @Mock
    private ApiImportRecordMapper importRecordMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiInterfaceServiceImpl service;

    // ==================== 创建 ====================

    @Test
    void createRejectsDuplicateNameInModule() {
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录"))
                .thenReturn(existingInterface("登录"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, WORKSPACE_ID, USER_ID, createReq("登录")));

        assertEquals(ErrorCodeConstants.API_INTERFACE_NAME_EXISTS.code(), ex.getCode());
        verify(interfaceMapper, never()).insert(any(ApiInterface.class));
    }

    @Test
    void createPersistsDefaultsAndWritesChangeLog() {
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录")).thenReturn(null);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        UUID id = service.create(PROJECT_ID, WORKSPACE_ID, USER_ID, createReq(null));

        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(captor.getValue().getProtocol()).isEqualTo("http");
        assertThat(captor.getValue().getStatus()).isEqualTo("enabled");
        assertThat(captor.getValue().getChangeVersion()).isEqualTo(1);
        assertThat(captor.getValue().getReferenceCount()).isZero();
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(USER_ID);

        ArgumentCaptor<ApiInterfaceChangeLog> logCaptor = ArgumentCaptor.forClass(ApiInterfaceChangeLog.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertEquals("create", logCaptor.getValue().getAction());
        assertThat(logCaptor.getValue().getChangeVersion()).isEqualTo(1);
    }

    // ==================== 更新（乐观锁） ====================

    @Test
    void updateRejectsStaleChangeVersion() {
        ApiInterface current = existingInterface("登录");
        current.setChangeVersion(2);
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(current);
        ApiInterfaceUpdateReqDTO req = updateReq(1);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, INTERFACE_ID, req));

        assertEquals(ErrorCodeConstants.API_INTERFACE_VERSION_CONFLICT.code(), ex.getCode());
        verify(interfaceMapper, never()).updateById(any(ApiInterface.class));
    }

    @Test
    void updateBumpsVersionAndWritesDiffSummary() {
        ApiInterface current = existingInterface("登录");
        current.setChangeVersion(2);
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(current);
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录")).thenReturn(current);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        ApiInterfaceUpdateReqDTO req = updateReq(2);
        req.setMethod("POST");
        req.setPath("/auth/login");

        service.update(PROJECT_ID, WORKSPACE_ID, USER_ID, INTERFACE_ID, req);

        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(INTERFACE_ID);
        assertThat(captor.getValue().getMethod()).isEqualTo("POST");
        assertThat(captor.getValue().getPath()).isEqualTo("/auth/login");
        assertThat(captor.getValue().getChangeVersion()).isEqualTo(3);

        ArgumentCaptor<ApiInterfaceChangeLog> logCaptor = ArgumentCaptor.forClass(ApiInterfaceChangeLog.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSummary()).contains("方法").contains("路径");
    }

    // ==================== 删除保护 ====================

    @Test
    void deleteRejectsReferencedInterface() {
        ApiInterface referenced = existingInterface("登录");
        referenced.setReferenceCount(3);
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(referenced);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.delete(PROJECT_ID, USER_ID, INTERFACE_ID));

        assertEquals(ErrorCodeConstants.API_INTERFACE_REFERENCED.code(), ex.getCode());
        verify(interfaceMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void batchDeleteAbortsAllWhenAnyReferenced() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ApiInterface free = existingInterface("空闲接口");
        free.setId(firstId);
        ApiInterface referenced = existingInterface("被引用接口");
        referenced.setId(secondId);
        referenced.setReferenceCount(1);
        when(interfaceMapper.selectById(firstId)).thenReturn(free);
        when(interfaceMapper.selectById(secondId)).thenReturn(referenced);

        ApiInterfaceBatchDeleteReqDTO req = new ApiInterfaceBatchDeleteReqDTO();
        req.setIds(List.of(firstId, secondId));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.batchDelete(PROJECT_ID, USER_ID, req));

        assertEquals(ErrorCodeConstants.API_INTERFACE_REFERENCED.code(), ex.getCode());
        verify(interfaceMapper, never()).deleteById(any(UUID.class));
    }

    // ==================== 视图分页 ====================

    @Test
    void pageFollowedViewWithNoFollowsReturnsEmptyPageImmediately() {
        when(followMapper.selectListByUserId(USER_ID)).thenReturn(List.of());

        PageResult<?> result = service.page(PROJECT_ID, WORKSPACE_ID, USER_ID,
                null, null, null, "followed", new PageParam());

        assertThat(result.getList()).isEmpty();
        verify(interfaceMapper, never()).selectPage(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pageCreatedViewFiltersByCreator() {
        when(followMapper.selectListByUserId(USER_ID)).thenReturn(List.of());
        PageParam pageParam = new PageParam();
        when(interfaceMapper.selectPage(PROJECT_ID, null, null, null, null, USER_ID, pageParam))
                .thenReturn(PageResult.empty());

        service.page(PROJECT_ID, WORKSPACE_ID, USER_ID, null, null, null, "created", pageParam);

        verify(interfaceMapper).selectPage(PROJECT_ID, null, null, null, null, USER_ID, pageParam);
    }

    // ==================== 关注 ====================

    @Test
    void followInsertsOnlyWhenAbsent() {
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(existingInterface("登录"));
        when(followMapper.selectByInterfaceAndUser(INTERFACE_ID, USER_ID)).thenReturn(null);

        service.follow(PROJECT_ID, USER_ID, INTERFACE_ID);

        ArgumentCaptor<ApiInterfaceFollow> captor = ArgumentCaptor.forClass(ApiInterfaceFollow.class);
        verify(followMapper).insert(captor.capture());
        assertThat(captor.getValue().getInterfaceId()).isEqualTo(INTERFACE_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    // ==================== 变量全量覆盖 ====================

    @Test
    void updateVariablesUpsertsByNamesAndDeletesMissing() {
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(existingInterface("登录"));
        ApiInterfaceVariable kept = variable("token", "abc");
        ApiInterfaceVariable dropped = variable("legacy", "x");
        List<ApiInterfaceVariable> snapshot = List.of(kept, dropped);
        when(variableMapper.selectListByInterfaceId(INTERFACE_ID))
                .thenReturn(snapshot).thenReturn(snapshot);

        ApiInterfaceVariablesReqDTO req = variablesReq(
                variableItem("token", "zzz"), variableItem("page", "1"));

        service.updateVariables(PROJECT_ID, USER_ID, INTERFACE_ID, req);

        ArgumentCaptor<ApiInterfaceVariable> updateCaptor = ArgumentCaptor.forClass(ApiInterfaceVariable.class);
        verify(variableMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(kept.getId());
        assertThat(updateCaptor.getValue().getDefaultValue()).isEqualTo("zzz");

        ArgumentCaptor<ApiInterfaceVariable> insertCaptor = ArgumentCaptor.forClass(ApiInterfaceVariable.class);
        verify(variableMapper).insert(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getName()).isEqualTo("page");

        verify(variableMapper).deleteById(dropped.getId());
    }

    // ==================== 预览 ====================

    @Test
    void previewMarksConflictsByPathAndMethod() {
        String content = """
                {
                  "info": {"name": "col"},
                  "item": [
                    {"name": "新建", "request": {"method": "POST", "url": "https://a.example.com/new"}},
                    {"name": "已有", "request": {"method": "POST", "url": "https://a.example.com/exists"}}
                  ]
                }
                """;
        when(interfaceMapper.selectByPathAndMethod(PROJECT_ID, "POST", "/new")).thenReturn(null);
        when(interfaceMapper.selectByPathAndMethod(PROJECT_ID, "POST", "/exists"))
                .thenReturn(existingInterface("已有"));

        ApiImportPreviewRespDTO resp = service.preview(PROJECT_ID, USER_ID,
                content.getBytes(StandardCharsets.UTF_8), null);

        assertThat(resp.getItems()).extracting(i -> i.getAction())
                .containsExactly("create", "update");
        assertThat(resp.getItems()).extracting(i -> i.getConflict()).containsExactly(false, true);
        assertThat(resp.getSummary()).containsEntry("toCreate", 1).containsEntry("toUpdate", 1);
        verify(importRecordMapper, never()).insert(any(ApiImportRecord.class));
    }

    // ==================== 文件导入 ====================

    @Test
    void importFileCreatesInterfacesMappingsAndRecord() {
        String content = """
                {
                  "info": {"name": "col"},
                  "item": [
                    {"name": "登录", "id": "req-9",
                     "request": {"method": "POST", "url": "https://api.example.com/auth/login"}}
                  ]
                }
                """;
        when(importMappingMapper.selectBySource(any(), any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, ApiInterface.class).setId(UUID.randomUUID());
            return 1;
        }).when(interfaceMapper).insert(any(ApiInterface.class));
        when(importRecordMapper.insert(any(ApiImportRecord.class))).thenReturn(1);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        ApiImportResultRespDTO resp = service.importFile(PROJECT_ID, USER_ID,
                content.getBytes(StandardCharsets.UTF_8), "collection.json", null);

        assertThat(resp.getSummary()).containsEntry("created", 1).containsEntry("failed", 0);

        ArgumentCaptor<ApiInterface> interfaceCaptor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(interfaceCaptor.capture());
        assertThat(interfaceCaptor.getValue().getName()).isEqualTo("登录");
        assertThat(interfaceCaptor.getValue().getProtocol()).isEqualTo("http");
        assertThat(interfaceCaptor.getValue().getStatus()).isEqualTo("enabled");
        assertThat(interfaceCaptor.getValue().getReferenceCount()).isZero();

        ArgumentCaptor<ApiImportMapping> mappingCaptor = ArgumentCaptor.forClass(ApiImportMapping.class);
        verify(importMappingMapper).insert(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getSourceType()).isEqualTo("postman_item");
        assertThat(mappingCaptor.getValue().getSourceId()).isEqualTo("req-9");
        assertThat(mappingCaptor.getValue().getAction()).isEqualTo("created");

        ArgumentCaptor<ApiImportRecord> recordCaptor = ArgumentCaptor.forClass(ApiImportRecord.class);
        verify(importRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo("success");
        assertThat(recordCaptor.getValue().getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    void importFileSuffixesDuplicateNames() {
        String content = """
                {
                  "info": {"name": "col"},
                  "item": [
                    {"name": "登录", "request": {"method": "POST", "url": "https://a.example.com/login"}}
                  ]
                }
                """;
        when(importMappingMapper.selectBySource(any(), any(), any())).thenReturn(null);
        when(interfaceMapper.selectByPathAndMethod(any(), any(), any())).thenReturn(null);
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录"))
                .thenReturn(existingInterface("登录"));
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, null, "登录 (2)")).thenReturn(null);
        doAnswer(invocation -> {
            invocation.getArgument(0, ApiInterface.class).setId(UUID.randomUUID());
            return 1;
        }).when(interfaceMapper).insert(any(ApiInterface.class));
        when(importRecordMapper.insert(any(ApiImportRecord.class))).thenReturn(1);
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        service.importFile(PROJECT_ID, USER_ID, content.getBytes(StandardCharsets.UTF_8),
                "collection.json", null);

        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("登录 (2)");
    }

    @Test
    void importFileWithUnknownFormatRaisesUnsupported() {
        byte[] garbage = "not a recognized format".getBytes(StandardCharsets.UTF_8);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.importFile(PROJECT_ID, USER_ID, garbage, "x.txt", null));

        assertEquals(ErrorCodeConstants.API_IMPORT_FORMAT_UNSUPPORTED.code(), ex.getCode());
    }

    // ==================== 复制 ====================

    @Test
    void copyClonesDefinitionWithoutStepsWhenSourceHasNone() {
        ApiInterface source = existingInterface("源接口");
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(source);
        when(interfaceMapper.selectByNameAndModule(PROJECT_ID, source.getModuleId(), "源接口（副本）"))
                .thenReturn(null);
        when(stepMapper.selectListByInterfaceId(INTERFACE_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.getArgument(0, ApiInterface.class).setId(UUID.randomUUID());
            return 1;
        }).when(interfaceMapper).insert(any(ApiInterface.class));
        when(changeLogMapper.insert(any(ApiInterfaceChangeLog.class))).thenReturn(1);

        UUID copyId = service.copy(PROJECT_ID, USER_ID, INTERFACE_ID, null);

        ArgumentCaptor<ApiInterface> captor = ArgumentCaptor.forClass(ApiInterface.class);
        verify(interfaceMapper).insert(captor.capture());
        assertThat(copyId).isNotEqualTo(INTERFACE_ID);
        assertThat(captor.getValue().getName()).isEqualTo("源接口（副本）");
        assertThat(captor.getValue().getReferenceCount()).isZero();
        assertThat(captor.getValue().getChangeVersion()).isEqualTo(1);
        verify(stepMapper, never()).insert(any(io.github.xiaomisum.robotest.model.entity.apitest.ApiInterfaceStep.class));
    }

    // ==================== 变更历史 ====================

    @Test
    void changeLogsReturnsPagedEntries() {
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(existingInterface("登录"));
        ApiInterfaceChangeLog log = new ApiInterfaceChangeLog();
        log.setInterfaceId(INTERFACE_ID);
        log.setChangeVersion(2);
        log.setAction("update");
        log.setSummary("修改 路径");
        PageParam pageParam = new PageParam();
        when(changeLogMapper.selectPageByInterfaceId(INTERFACE_ID, pageParam))
                .thenReturn(new PageResult<>(List.of(log), 1L));

        PageResult<?> result = service.changeLogs(PROJECT_ID, USER_ID, INTERFACE_ID, pageParam);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).first()
                .hasFieldOrPropertyWithValue("action", "update")
                .hasFieldOrPropertyWithValue("changeVersion", 2);
    }

    // ==================== 辅助 ====================

    private ApiInterfaceCreateReqDTO createReq(String name) {
        ApiInterfaceCreateReqDTO req = new ApiInterfaceCreateReqDTO();
        req.setName(name == null ? "登录" : name);
        req.setMethod("POST");
        req.setPath("/auth/login");
        return req;
    }

    /** 基线更新请求：与 existingInterface 的 GET /users 同名同路径，避免触发无关差异 */
    private ApiInterfaceUpdateReqDTO updateReq(int changeVersion) {
        ApiInterfaceUpdateReqDTO req = new ApiInterfaceUpdateReqDTO();
        req.setName("登录");
        req.setMethod("GET");
        req.setPath("/users");
        req.setChangeVersion(changeVersion);
        return req;
    }

    private ApiInterfaceVariablesReqDTO variablesReq(ApiInterfaceVariablesReqDTO.VariableItem... items) {
        ApiInterfaceVariablesReqDTO req = new ApiInterfaceVariablesReqDTO();
        req.setVariables(List.of(items));
        return req;
    }

    private ApiInterfaceVariablesReqDTO.VariableItem variableItem(String name, String value) {
        ApiInterfaceVariablesReqDTO.VariableItem item = new ApiInterfaceVariablesReqDTO.VariableItem();
        item.setName(name);
        item.setDefaultValue(value);
        return item;
    }

    private ApiInterface existingInterface(String name) {
        ApiInterface entity = new ApiInterface();
        entity.setId(INTERFACE_ID);
        entity.setProjectId(PROJECT_ID);
        entity.setModuleId(null);
        entity.setName(name);
        entity.setProtocol("http");
        entity.setMethod("GET");
        entity.setPath("/users");
        entity.setStatus("enabled");
        entity.setChangeVersion(1);
        entity.setReferenceCount(0);
        return entity;
    }

    private ApiInterfaceFollow followRow(UUID interfaceId) {
        ApiInterfaceFollow row = new ApiInterfaceFollow();
        row.setInterfaceId(interfaceId);
        row.setUserId(USER_ID);
        return row;
    }

    private ApiInterfaceVariable variable(String name, String value) {
        ApiInterfaceVariable entity = new ApiInterfaceVariable();
        entity.setId(UUID.randomUUID());
        entity.setInterfaceId(INTERFACE_ID);
        entity.setName(name);
        entity.setDefaultValue(value);
        return entity;
    }
}
