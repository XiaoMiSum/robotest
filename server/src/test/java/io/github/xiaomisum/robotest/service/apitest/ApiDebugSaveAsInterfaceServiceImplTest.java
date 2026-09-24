package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 调试记录保存为接口：create / attach 模式、UI 快照覆盖、响应示例透传 */
@ExtendWith(MockitoExtension.class)
class ApiDebugSaveAsInterfaceServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID ENVIRONMENT_ID = UUID.randomUUID();
    private static final UUID INTERFACE_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();

    @Mock
    private ApiDebugRecordMapper debugRecordMapper;
    @Mock
    private ApiInterfaceService interfaceService;
    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiDebugSaveAsInterfaceServiceImpl service;

    @Test
    void saveAsInterfaceCreatesInterfaceWithMappedSnapshot() {
        ApiDebugRecord record = ownedRecord();
        record.setEnvironmentId(ENVIRONMENT_ID);
        record.setMethod("POST");
        record.setUrl("https://staging.example.com/api/auth/login?src=curl&flag=1");
        record.setBodyType("json");
        record.setBody(Map.of("username", "admin"));
        record.setQueryParams(List.of(Map.of("key", "src", "value", "db", "enabled", true)));
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        stubDefaultHttpBaseUrl("https://staging.example.com");
        when(interfaceService.create(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(INTERFACE_ID);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("  用户登录  ");
        reqDTO.setModuleId(MODULE_ID);
        UUID result = service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        assertEquals(INTERFACE_ID, result);
        ArgumentCaptor<ApiInterfaceCreateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceCreateReqDTO.class);
        verify(interfaceService).create(any(), any(), any(), captor.capture());
        ApiInterfaceCreateReqDTO payload = captor.getValue();
        assertEquals("用户登录", payload.getName());
        assertEquals(MODULE_ID, payload.getModuleId());
        assertEquals("http", payload.getProtocol());
        assertEquals("POST", payload.getMethod());
        assertEquals("/api/auth/login", payload.getPath());
        assertThat(payload.getParams()).extracting(p -> p.get("key"))
                .containsExactlyInAnyOrder("src", "flag");
        Object srcParam = payload.getParams().stream()
                .filter(p -> "src".equals(p.get("key")))
                .findFirst().orElseThrow();
        assertEquals("db", ((Map<?, ?>) srcParam).get("value"));
        assertThat(payload.getBody())
                .containsEntry("type", "json")
                .containsEntry("content", Map.of("username", "admin"));
    }

    @Test
    void saveAsInterfaceWithoutEnvKeepsOnlyPath() {
        ApiDebugRecord record = ownedRecord();
        record.setMethod("GET");
        record.setUrl("https://staging.example.com/users?page=2");
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        ApiEnvironment environment = new ApiEnvironment();
        environment.setProjectId(PROJECT_ID);
        environment.setHttpConfigs(List.of(Map.of("baseUrl", "https://staging.example.com")));
        when(environmentMapper.findDefaultByProjectId(PROJECT_ID)).thenReturn(environment);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("用户列表");
        reqDTO.setModuleId(MODULE_ID);
        service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        ArgumentCaptor<ApiInterfaceCreateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceCreateReqDTO.class);
        verify(interfaceService).create(any(), any(), any(), captor.capture());
        verify(environmentMapper, atLeastOnce()).findDefaultByProjectId(PROJECT_ID);
        assertEquals("/users", captor.getValue().getPath());
    }

    @Test
    void saveAsInterfaceAttachesToExistingInterface() {
        ApiDebugRecord record = ownedRecord();
        record.setMethod("PUT");
        record.setUrl("/users/1");
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        ApiInterface target = new ApiInterface();
        target.setId(INTERFACE_ID);
        target.setProjectId(PROJECT_ID);
        target.setName("用户登录");
        target.setModuleId(MODULE_ID);
        target.setChangeVersion(3);
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(target);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("attach");
        reqDTO.setInterfaceId(INTERFACE_ID);
        reqDTO.setChangeVersion(3);
        UUID result = service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        assertEquals(INTERFACE_ID, result);
        ArgumentCaptor<ApiInterfaceUpdateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceUpdateReqDTO.class);
        verify(interfaceService).update(any(), any(), any(), eq(INTERFACE_ID), captor.capture());
        ApiInterfaceUpdateReqDTO payload = captor.getValue();
        assertEquals("用户登录", payload.getName());
        assertEquals(MODULE_ID, payload.getModuleId());
        assertEquals(Integer.valueOf(3), payload.getChangeVersion());
        assertEquals("PUT", payload.getMethod());
        assertEquals("/users/1", payload.getPath());
    }

    @Test
    void saveAsInterfaceRejectsForeignRecord() {
        ApiDebugRecord record = ownedRecord();
        record.setUserId(UUID.randomUUID());
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("任意");
        reqDTO.setModuleId(MODULE_ID);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO));
        assertEquals(ErrorCodeConstants.API_DEBUG_RECORD_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void saveAsInterfaceAttachMissingTargetThrowsNotFound() {
        ApiDebugRecord record = ownedRecord();
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        when(interfaceMapper.selectById(INTERFACE_ID)).thenReturn(null);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("attach");
        reqDTO.setInterfaceId(INTERFACE_ID);
        reqDTO.setChangeVersion(1);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO));
        assertEquals(ErrorCodeConstants.API_INTERFACE_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void saveAsInterfaceUsesUiRequestSnapshot() {
        ApiDebugRecord record = ownedRecord();
        record.setEnvironmentId(ENVIRONMENT_ID);
        record.setMethod("GET");
        record.setUrl("https://staging.example.com/old-path");
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        stubDefaultHttpBaseUrl("https://staging.example.com");
        when(interfaceService.create(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(INTERFACE_ID);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("UI 请求快照");
        reqDTO.setModuleId(MODULE_ID);
        reqDTO.setRequest(Map.of(
                "method", "PATCH",
                "url", "https://staging.example.com/api/users/1?active=true",
                "headers", List.of(Map.of("key", "X-Token", "value", "abc", "enabled", true)),
                "params", List.of(),
                "body", Map.of("type", "json", "content", Map.of("name", "新名字"))));
        UUID result = service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        assertEquals(INTERFACE_ID, result);
        ArgumentCaptor<ApiInterfaceCreateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceCreateReqDTO.class);
        verify(interfaceService).create(any(), any(), any(), captor.capture());
        ApiInterfaceCreateReqDTO payload = captor.getValue();
        assertEquals("PATCH", payload.getMethod());
        assertEquals("/api/users/1", payload.getPath());
        assertThat(payload.getHeaders()).extracting(h -> h.get("key")).containsExactly("X-Token");
        assertThat(payload.getBody())
                .containsEntry("type", "json")
                .containsEntry("content", Map.of("name", "新名字"));
    }

    @Test
    void saveAsInterfaceIncludesResponseExample() {
        ApiDebugRecord record = ownedRecord();
        record.setEnvironmentId(ENVIRONMENT_ID);
        record.setMethod("POST");
        record.setUrl("/api/auth/login");
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);
        stubDefaultHttpBaseUrl("https://api.example.com");
        when(interfaceService.create(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(INTERFACE_ID);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("带响应示例");
        reqDTO.setModuleId(MODULE_ID);
        reqDTO.setRequest(Map.of("method", "POST", "url", "/api/auth/login"));
        reqDTO.setResponseExample(Map.of(
                "status", 200,
                "headers", Map.of("Content-Type", "application/json"),
                "body", Map.of("token", "eyJhbGciOiJIUzI1NiJ9")));
        service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        ArgumentCaptor<ApiInterfaceCreateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceCreateReqDTO.class);
        verify(interfaceService).create(any(), any(), any(), captor.capture());
        ApiInterfaceCreateReqDTO payload = captor.getValue();
        assertThat(payload.getResponseExample())
                .containsEntry("status", 200)
                .containsEntry("body", Map.of("token", "eyJhbGciOiJIUzI1NiJ9"));
    }

    // ========== helpers ==========

    private ApiDebugRecord ownedRecord() {
        ApiDebugRecord record = new ApiDebugRecord();
        record.setId(RECORD_ID);
        record.setProjectId(PROJECT_ID);
        record.setUserId(USER_ID);
        return record;
    }

    private void stubDefaultHttpBaseUrl(String baseUrl) {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(ENVIRONMENT_ID);
        env.setProjectId(PROJECT_ID);
        LinkedHashMap<String, Object> http = new LinkedHashMap<>();
        http.put("name", "默认配置");
        http.put("refName", "http_1");
        http.put("baseUrl", baseUrl);
        http.put("headers", List.of());
        env.setHttpConfigs(List.of(http));
        when(environmentMapper.selectById(ENVIRONMENT_ID)).thenReturn(env);
    }
}
