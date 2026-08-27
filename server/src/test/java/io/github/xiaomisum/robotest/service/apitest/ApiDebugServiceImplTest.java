package io.github.xiaomisum.robotest.service.apitest;

import com.sun.net.httpserver.HttpServer;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 快速调试服务：执行、cURL 导入、记录管理 */
@ExtendWith(MockitoExtension.class)
class ApiDebugServiceImplTest {

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
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ApiEnvironmentHttpMapper environmentHttpMapper;
    @Mock
    private ApiEnvironmentVariableMapper environmentVariableMapper;
    @Mock
    private ApiEnvironmentProcessorMapper environmentProcessorMapper;
    @Mock
    private ApiInterfaceService interfaceService;
    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiDebugServiceImpl service;

    private ThreadPoolTaskExecutor executor;
    private ThreadPoolTaskExecutor persistExecutor;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.afterPropertiesSet();

        persistExecutor = new ThreadPoolTaskExecutor();
        persistExecutor.setCorePoolSize(1);
        persistExecutor.setMaxPoolSize(1);
        persistExecutor.setQueueCapacity(10);
        persistExecutor.afterPropertiesSet();

        ApiTestProperties properties = new ApiTestProperties();
        ReflectionSet.set(service, "apiTestExecutor", executor);
        ReflectionSet.set(service, "persistExecutor", persistExecutor);
        ReflectionSet.set(service, "properties", properties);

        // 环境快照装配已抽取为共享工厂，测试内以同一组环境 mock 组装真实工厂注入
        EnvironmentSnapshotFactory environmentSnapshotFactory = new EnvironmentSnapshotFactory();
        ReflectionSet.set(environmentSnapshotFactory, "environmentMapper", environmentMapper);
        ReflectionSet.set(environmentSnapshotFactory, "environmentHttpMapper", environmentHttpMapper);
        ReflectionSet.set(environmentSnapshotFactory, "environmentVariableMapper", environmentVariableMapper);
        ReflectionSet.set(environmentSnapshotFactory, "environmentProcessorMapper", environmentProcessorMapper);
        ReflectionSet.set(service, "environmentSnapshotFactory", environmentSnapshotFactory);

        // 执行前自定义函数注入依赖运行时，测试中装配真实实例（mapper 交互均被 mock）
        CustomFunctionRuntime functionRuntime = new CustomFunctionRuntime(
                org.mockito.Mockito.mock(io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper.class),
                org.mockito.Mockito.mock(io.github.xiaomisum.robotest.repository.workspace.ProjectMapper.class),
                new ApiFunctionScriptEngine());
        ReflectionSet.set(service, "functionRuntime", functionRuntime);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        executor.shutdown();
        persistExecutor.shutdown();
    }

    @Test
    void executePostsRequestAndPersistsRecord() throws Exception {
        startEchoServer(() -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            return new Object[]{200, body};
        });
        int port = httpServer.getAddress().getPort();

        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("POST");
        req.setUrl("http://127.0.0.1:" + port + "/auth/login");
        ApiDebugExecuteReqDTO.Body body = new ApiDebugExecuteReqDTO.Body();
        body.setType("json");
        body.setContent(Map.of("username", "admin"));
        req.setBody(body);
        req.setHeaders(List.of(Map.of("key", "X-Tag", "value", "debug", "enabled", true)));

        var resp = service.execute(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        assertThat(resp.getStatus()).isEqualTo("success");
        assertThat(resp.getResponseStatus()).isEqualTo(200);
        assertThat(resp.getResponseBody()).isEqualTo(Map.of("ok", true));
        assertThat(resp.getSize()).isGreaterThan(0);

        ArgumentCaptor<ApiDebugRecord> captor = ArgumentCaptor.forClass(ApiDebugRecord.class);
        verify(debugRecordMapper).insert(captor.capture());
        ApiDebugRecord record = captor.getValue();
        assertEquals("success", record.getStatus());
        assertEquals("POST /auth/login", record.getName());
        assertThat(record.getUserId()).isEqualTo(USER_ID);
        assertThat(record.getProjectId()).isEqualTo(PROJECT_ID);
        assertTrue(persistDrained());
    }

    @Test
    void executeUnreachableUrlMarksErrorWithoutThrowing() {
        // 端口 1 保留端口，连接必然快速失败
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("GET");
        req.setUrl("http://127.0.0.1:1/health");

        var resp = service.execute(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        assertThat(resp.getStatus()).isEqualTo("error");
        assertThat(resp.getErrorMessage()).isNotBlank();
    }

    @Test
    void importCurlMapsParsedCommand() {
        String curl = "curl -X POST 'https://staging.example.com/api/auth/login' "
                + "-H 'Content-Type: application/json' -d '{\"u\":1}'";
        var resp = service.importCurl(PROJECT_ID, WORKSPACE_ID, USER_ID, curl);

        assertEquals("POST", resp.getMethod());
        assertThat(resp.getBody().getType()).isEqualTo("json");
        assertThat(resp.getBody().getContent()).isEqualTo(Map.of("u", 1));
    }

    @Test
    void importCurlWithoutUrlRaisesParseFailed() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.importCurl(PROJECT_ID, WORKSPACE_ID, USER_ID, "curl -X POST"));
        assertEquals(ErrorCodeConstants.API_IMPORT_PARSE_FAILED.code(), ex.getCode());
    }

    @Test
    void renameOnlyCarriesNameField() {
        ApiDebugRecord existing = new ApiDebugRecord();
        existing.setId(RECORD_ID);
        existing.setProjectId(PROJECT_ID);
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(existing);

        ApiDebugRenameReqDTO reqDTO = new ApiDebugRenameReqDTO();
        reqDTO.setName("登录调试");
        service.renameRecord(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        ArgumentCaptor<ApiDebugRecord> captor = ArgumentCaptor.forClass(ApiDebugRecord.class);
        verify(debugRecordMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("登录调试");
        // C9：更新载体仅携带 id 与变更字段，其余字段保持 null
        assertThat(captor.getValue().getUrl()).isNull();
    }

    @Test
    void deleteUnknownRecordThrowsNotFound() {
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.deleteRecord(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID));
        assertEquals(ErrorCodeConstants.API_DEBUG_RECORD_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void pageRecordsMapsItems() {
        ApiDebugRecord row = new ApiDebugRecord();
        row.setId(RECORD_ID);
        row.setProjectId(PROJECT_ID);
        row.setName("GET /users");
        row.setMethod("GET");
        row.setStatus("success");
        when(debugRecordMapper.selectPage(any(UUID.class), any(UUID.class), any(), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(row), 1L));

        PageResult<?> page = service.pageRecords(PROJECT_ID, WORKSPACE_ID, USER_ID, null, new PageParam());
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    void restoreReturnsSnapshotAndResponse() {
        ApiDebugRecord record = new ApiDebugRecord();
        record.setId(RECORD_ID);
        record.setProjectId(PROJECT_ID);
        record.setMethod("POST");
        record.setUrl("/login");
        record.setHeaders(List.of(Map.of("key", "A", "value", "b", "enabled", true)));
        record.setBodyType("json");
        record.setResponseBody("{\"code\":200}");
        record.setResponseStatus(200);
        record.setDurationMs(120);
        record.setResponseSize(11);
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        var resp = service.restore(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID);
        assertThat(resp.getRequest().getMethod()).isEqualTo("POST");
        assertThat(resp.getResponse().getBody()).isEqualTo(Map.of("code", 200));
        assertThat(resp.getResponse().getElapsed()).isEqualTo(120);
        assertThat(resp.getDebugRecordId()).isEqualTo(RECORD_ID.toString());
    }

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
        // baseUrl 剥离得相对路径
        assertEquals("/api/auth/login", payload.getPath());
        // URL query 与已配置参数合并，同名以已配置值为准
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
    void saveAsInterfaceWithoutEnvKeepsAbsoluteUrl() {
        ApiDebugRecord record = ownedRecord();
        record.setMethod("GET");
        record.setUrl("https://other.example.com:8443/users?page=2");
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        ApiDebugSaveAsInterfaceReqDTO reqDTO = new ApiDebugSaveAsInterfaceReqDTO();
        reqDTO.setMode("create");
        reqDTO.setName("用户列表");
        reqDTO.setModuleId(MODULE_ID);
        service.saveAsInterface(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID, reqDTO);

        ArgumentCaptor<ApiInterfaceCreateReqDTO> captor =
                ArgumentCaptor.forClass(ApiInterfaceCreateReqDTO.class);
        verify(interfaceService).create(any(), any(), any(), captor.capture());
        // 环境缺失无法剥离 baseUrl，保留完整 URL（query 并入 params）
        assertEquals("https://other.example.com:8443/users", captor.getValue().getPath());
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
        // 名称/模块取自目标接口原样保留，仅覆盖主请求字段
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
        when(environmentMapper.selectById(ENVIRONMENT_ID)).thenReturn(env);
        ApiEnvironmentHttp http = new ApiEnvironmentHttp();
        http.setIsDefault(true);
        http.setBaseUrl(baseUrl);
        when(environmentHttpMapper.listByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(http));
    }

    private void startEchoServer(EchoResponder responder) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            Object[] payload = responder.respond();
            exchange.sendResponseHeaders((int) payload[0], ((byte[]) payload[1]).length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write((byte[]) payload[1]);
            }
        });
        httpServer.start();
    }

    private interface EchoResponder {
        Object[] respond();
    }

    /** 等待异步落库队列清空，保证 verify(insert) 时序稳定 */
    private boolean persistDrained() throws InterruptedException {
        for (int i = 0; i < 50 && persistExecutor.getQueueSize() > 0; i++) {
            Thread.sleep(20);
        }
        Thread.sleep(100);
        return true;
    }

    /** 测试内反射注入 @Resource 字段 */
    static final class ReflectionSet {
        static void set(Object target, String field, Object value) {
            try {
                var declared = target.getClass().getDeclaredField(field);
                declared.setAccessible(true);
                declared.set(target, value);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
