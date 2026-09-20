package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeEnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeSuiteRunner;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.MappedResult;

import com.sun.net.httpserver.HttpServer;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDebugRecord;
import io.github.xiaomisum.robotest.repository.apitest.ApiDebugRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.ryze.TestStatus;
import io.github.xiaomisum.ryze.result.AssertionResult;
import io.github.xiaomisum.ryze.testelement.TestSuiteResult;
import io.github.xiaomisum.ryze.testelement.sampler.DefaultSampleResult;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 快速调试服务：执行、记录管理 */
@ExtendWith(MockitoExtension.class)
class ApiDebugServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();

    @Mock
    private ApiDebugRecordMapper debugRecordMapper;
    @Mock
    private ApiEnvironmentMapper environmentMapper;
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

        RyzeEnvironmentSnapshotProvider environmentSnapshotFactory = new RyzeEnvironmentSnapshotProvider();
        ReflectionSet.set(environmentSnapshotFactory, "environmentMapper", environmentMapper);
        ReflectionSet.set(service, "environmentSnapshotFactory", environmentSnapshotFactory);

        ReflectionSet.set(service, "suiteRunner", new RyzeSuiteRunner(new RyzeResultMapper(properties)));

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
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("GET");
        req.setUrl("http://127.0.0.1:1/health");

        var resp = service.execute(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        assertThat(resp.getStatus()).isEqualTo("error");
        assertThat(resp.getErrorMessage()).isNotBlank();
    }

    @Test
    void assertionFailureMarksDebugFailed() throws Exception {
        DefaultSampleResult sample = new DefaultSampleResult("步骤");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new AssertionError("期望 0 实际 1"));
        AssertionResult assertion = new AssertionResult();
        assertion.setField("$.code");
        assertion.setExpected(0);
        assertion.setActual(1);
        assertion.setStatus(TestStatus.failed);
        sample.addAssertion(assertion);
        TestSuiteResult top = new TestSuiteResult("调试");
        top.setStatus(TestStatus.failed);
        top.addChild(sample);

        Object snapshot = collect(mapped(top));

        assertEquals("failed", accessor(snapshot, "status"));
    }

    @Test
    void exceptionGroupExpandsSubMessagesInDebugError() throws Exception {
        DefaultSampleResult sample = new DefaultSampleResult("步骤");
        sample.setStatus(TestStatus.broken);
        sample.setThrowable(new io.github.xiaomisum.ryze.support.ExceptionGroup("提取器执行失败", List.of(
                new IllegalArgumentException("未提取到数据且无默认值，表达式: $.token"))));
        TestSuiteResult top = new TestSuiteResult("调试");
        top.setStatus(TestStatus.broken);
        top.addChild(sample);

        Object snapshot = collect(mapped(top));

        assertEquals("error", accessor(snapshot, "status"));
        assertTrue(((String) accessor(snapshot, "errorMessage")).contains("未提取到数据且无默认值"));
    }

    private Object collect(MappedResult result) throws Exception {
        var collect = ApiDebugServiceImpl.class.getDeclaredMethod("collect", MappedResult.class);
        collect.setAccessible(true);
        return collect.invoke(service, result);
    }

    private MappedResult mapped(io.github.xiaomisum.ryze.Result result) {
        return RyzeResultMapper.map(result, 20000);
    }

    private Object accessor(Object snapshot, String name) throws Exception {
        var method = snapshot.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(snapshot);
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
    void restoreRebuildsFormBodyAsTypeContentStructure() {
        ApiDebugRecord record = new ApiDebugRecord();
        record.setId(RECORD_ID);
        record.setProjectId(PROJECT_ID);
        record.setMethod("POST");
        record.setUrl("/login");
        record.setBodyType("form");
        record.setBody(Map.of("content", List.of(
                Map.of("key", "name", "value", "张三", "enabled", true),
                Map.of("key", "next", "value", "a=1&b=2", "enabled", true))));
        when(debugRecordMapper.selectById(RECORD_ID)).thenReturn(record);

        var resp = service.restore(PROJECT_ID, WORKSPACE_ID, USER_ID, RECORD_ID);
        Map<String, Object> body = resp.getRequest().getBody();
        assertThat(body.get("type")).isEqualTo("form");
        assertThat(body.get("content")).isEqualTo(List.of(
                Map.of("key", "name", "value", "张三", "enabled", true),
                Map.of("key", "next", "value", "a=1&b=2", "enabled", true)));
    }

    // ========== helpers ==========

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

    private boolean persistDrained() throws InterruptedException {
        for (int i = 0; i < 50 && persistExecutor.getQueueSize() > 0; i++) {
            Thread.sleep(20);
        }
        Thread.sleep(100);
        return true;
    }

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
