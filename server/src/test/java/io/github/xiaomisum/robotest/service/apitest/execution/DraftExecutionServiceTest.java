package io.github.xiaomisum.robotest.service.apitest.execution;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeEnvironmentSnapshotProvider;

import com.sun.net.httpserver.HttpServer;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.apitest.ApiFunctionScriptEngine;
import io.github.xiaomisum.robotest.service.apitest.CustomFunctionRuntime;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeResultMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeSuiteRunner;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneSuiteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import xyz.migoo.framework.common.exception.ServiceException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 草稿（创建态未保存场景）调试/执行：用页面实时数据，复用 Ryze 引擎。
 * 走真实 HttpServer 回显验证实际请求命中与响应解析；编排经 Launcher 真实引擎接缝（RyzeSuiteRunner/SceneSuiteBuilder）。
 */
@ExtendWith(MockitoExtension.class)
class DraftExecutionServiceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiReportMapper reportMapper;
    @Mock
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ApiEnvironmentMapper environmentMapper;

    private DraftExecutionService service;
    private ThreadPoolTaskExecutor executor;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.afterPropertiesSet();

        ApiTestProperties properties = new ApiTestProperties();
        RyzeEnvironmentSnapshotProvider envFactory = new RyzeEnvironmentSnapshotProvider();
        ReflectionSet.set(envFactory, "environmentMapper", environmentMapper);

        CustomFunctionRuntime functionRuntime = new CustomFunctionRuntime(
                mock(ApiFunctionMapper.class),
                mock(ProjectMapper.class),
                new ApiFunctionScriptEngine());

        SceneExecutionLauncher launcher = new SceneExecutionLauncher();
        ReflectionSet.set(launcher, "sceneMapper", sceneMapper);
        ReflectionSet.set(launcher, "executionRecordMapper", executionRecordMapper);
        ReflectionSet.set(launcher, "reportMapper", reportMapper);
        ReflectionSet.set(launcher, "projectAccessGuard", projectAccessGuard);
        ReflectionSet.set(launcher, "apiTestExecutor", executor);
        ReflectionSet.set(launcher, "properties", properties);
        ReflectionSet.set(launcher, "environmentSnapshotFactory", envFactory);
        ReflectionSet.set(launcher, "functionRuntime", functionRuntime);
        ReflectionSet.set(launcher, "suiteRunner", new RyzeSuiteRunner(new RyzeResultMapper(properties)));
        ReflectionSet.set(launcher, "suiteBuilder", new SceneSuiteBuilder());
        ReflectionSet.set(launcher, "cancelRegistry", new ExecutionCancelRegistry());

        service = new DraftExecutionService();
        ReflectionSet.set(service, "projectAccessGuard", projectAccessGuard);
        ReflectionSet.set(service, "sceneMapper", sceneMapper);
        ReflectionSet.set(service, "environmentSnapshotFactory", envFactory);
        ReflectionSet.set(service, "suiteBuilder", new SceneSuiteBuilder());
        ReflectionSet.set(service, "properties", properties);
        ReflectionSet.set(service, "launcher", launcher);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        executor.shutdown();
    }

    @Test
    void draftExecute_runsAllStepsInOrder_stopsOnFailure() throws Exception {
        startEchoServer(() -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            return new Object[]{200, body};
        });
        int port = httpServer.getAddress().getPort();

        ApiSceneDraftExecuteReqDTO req = new ApiSceneDraftExecuteReqDTO();
        req.setName("草稿场景");
        ApiSceneDraftExecuteReqDTO.DraftStep s1 = new ApiSceneDraftExecuteReqDTO.DraftStep();
        s1.setName("第一步");
        s1.setEnabled(true);
        s1.setRequestConfig(Map.of("method", "GET", "url", "http://127.0.0.1:" + port + "/ok"));
        ApiSceneDraftExecuteReqDTO.DraftStep s2 = new ApiSceneDraftExecuteReqDTO.DraftStep();
        s2.setName("第二步");
        s2.setEnabled(true);
        s2.setRequestConfig(Map.of("method", "GET", "url", "http://127.0.0.1:" + port + "/ok"));
        req.setSteps(List.of(s1, s2));

        ApiSceneDraftExecuteRespDTO resp = service.draftExecute(WORKSPACE_ID, PROJECT_ID, USER_ID, req);

        assertThat(resp.getStatus()).isEqualTo("success");
        assertThat(resp.getPassed()).isEqualTo(2);
        assertThat(resp.getFailed()).isZero();
    }

    @Test
    void draftExecute_disabledThenMissingConfig_stopsOnFailure() {
        ApiSceneDraftExecuteReqDTO req = new ApiSceneDraftExecuteReqDTO();
        ApiSceneDraftExecuteReqDTO.DraftStep s1 = new ApiSceneDraftExecuteReqDTO.DraftStep();
        s1.setName("停用");
        s1.setEnabled(false);
        s1.setRequestConfig(Map.of("method", "GET", "url", "http://127.0.0.1:1/x"));
        ApiSceneDraftExecuteReqDTO.DraftStep s2 = new ApiSceneDraftExecuteReqDTO.DraftStep();
        s2.setName("缺配置");
        s2.setEnabled(true);
        s2.setRequestConfig(null);
        req.setSteps(List.of(s1, s2));

        ApiSceneDraftExecuteRespDTO resp = service.draftExecute(WORKSPACE_ID, PROJECT_ID, USER_ID, req);

        assertThat(resp.getSkipped()).isEqualTo(1);
        assertThat(resp.getStatus()).isEqualTo("failed");
        assertThat(resp.getSteps()).extracting(s -> s.getStatus())
                .containsExactly("skipped", "error");
    }

    @Test
    void draftExecute_emptySteps_throwsValidation() {
        ApiSceneDraftExecuteReqDTO req = new ApiSceneDraftExecuteReqDTO();
        req.setSteps(List.of());
        assertThatThrownBy(() -> service.draftExecute(WORKSPACE_ID, PROJECT_ID, USER_ID, req))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(1000001001);
    }

    // ========== 工具 ==========

    private void startEchoServer(EchoResponder responder) throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", exchange -> {
            Object[] result = responder.respond();
            int status = (int) result[0];
            byte[] body = (byte[]) result[1];
            try (var os = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(status, body.length);
                os.write(body);
            }
        });
        httpServer.start();
    }

    @FunctionalInterface
    private interface EchoResponder {
        Object[] respond();
    }

    private static class ReflectionSet {
        static void set(Object target, String field, Object value) {
            try {
                var f = target.getClass().getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}