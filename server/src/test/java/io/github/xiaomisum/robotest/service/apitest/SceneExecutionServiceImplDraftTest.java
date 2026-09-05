package io.github.xiaomisum.robotest.service.apitest;

import com.sun.net.httpserver.HttpServer;
import io.github.xiaomisum.robotest.framework.config.ApiTestProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneDraftExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepDraftDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneDraftExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSceneStepDebugRespDTO;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiReportMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
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
 * 走真实 HttpServer 回显验证实际请求命中与响应解析。
 */
@ExtendWith(MockitoExtension.class)
class SceneExecutionServiceImplDraftTest {

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

    private SceneExecutionServiceImpl service;
    private ThreadPoolTaskExecutor executor;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.afterPropertiesSet();

        service = new SceneExecutionServiceImpl();
        ReflectionSet.set(service, "sceneMapper", sceneMapper);
        ReflectionSet.set(service, "executionRecordMapper", executionRecordMapper);
        ReflectionSet.set(service, "reportMapper", reportMapper);
        ReflectionSet.set(service, "changeHistoryMapper", changeHistoryMapper);
        ReflectionSet.set(service, "userMapper", userMapper);
        ReflectionSet.set(service, "projectAccessGuard", projectAccessGuard);
        ReflectionSet.set(service, "apiTestExecutor", executor);
        ReflectionSet.set(service, "properties", new ApiTestProperties());

        EnvironmentSnapshotFactory envFactory = new EnvironmentSnapshotFactory();
        ReflectionSet.set(envFactory, "environmentMapper", environmentMapper);
        ReflectionSet.set(service, "environmentSnapshotFactory", envFactory);

        CustomFunctionRuntime functionRuntime = new CustomFunctionRuntime(
                mock(io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper.class),
                mock(io.github.xiaomisum.robotest.repository.workspace.ProjectMapper.class),
                new ApiFunctionScriptEngine());
        ReflectionSet.set(service, "functionRuntime", functionRuntime);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        executor.shutdown();
    }

    @Test
    void draftDebugStep_executesWithLiveSceneVariables() throws Exception {
        startEchoServer(() -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            return new Object[]{200, body};
        });
        int port = httpServer.getAddress().getPort();

        ApiSceneStepDraftDebugReqDTO req = new ApiSceneStepDraftDebugReqDTO();
        ApiSceneVariableBatchReqDTO.Variable var = new ApiSceneVariableBatchReqDTO.Variable();
        var.setName("token");
        var.setValue("abc");
        req.setSceneVariables(List.of(var));
        ApiSceneStepDraftDebugReqDTO.Step step = new ApiSceneStepDraftDebugReqDTO.Step();
        step.setName("登录");
        step.setRequestConfig(Map.of("method", "GET",
                "url", "http://127.0.0.1:" + port + "/auth?token=${token}"));
        req.setStep(step);

        ApiSceneStepDebugRespDTO resp = service.draftDebugStep(WORKSPACE_ID, PROJECT_ID, USER_ID, req);

        assertThat(resp.getStepResult().getStatus()).isEqualTo("success");
        assertThat(resp.getStepResult().getResponse().get("status")).isEqualTo(200);
    }

    @Test
    void draftDebugStep_missingConfig_returnsErrorWithoutExecuting() {
        ApiSceneStepDraftDebugReqDTO req = new ApiSceneStepDraftDebugReqDTO();
        ApiSceneStepDraftDebugReqDTO.Step step = new ApiSceneStepDraftDebugReqDTO.Step();
        step.setName("无配置");
        step.setRequestConfig(null);
        req.setStep(step);

        ApiSceneStepDebugRespDTO resp = service.draftDebugStep(WORKSPACE_ID, PROJECT_ID, USER_ID, req);

        assertThat(resp.getStepResult().getStatus()).isEqualTo("error");
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