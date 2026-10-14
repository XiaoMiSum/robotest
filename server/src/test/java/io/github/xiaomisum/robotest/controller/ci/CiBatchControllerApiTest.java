package io.github.xiaomisum.robotest.controller.ci;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.CiTokenAuthFilter;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitBatchCompletionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitBatchManifestRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitBatchService;
import io.github.xiaomisum.robotest.service.apitest.ApiGitCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.migoo.framework.apilog.core.ApiErrorLogFrameworkService;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.web.core.handler.GlobalExceptionHandler;
import xyz.migoo.framework.web.i18n.I18NMessage;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 集成测试（GitLab 集成详细设计 4.6）：CI 侧批次清单与完成回报的路由、校验与鉴权链。
 *
 * <p>CI 端点无用户上下文（C4 不适用），认证由 {@link CiTokenAuthFilter} 承担——本测试把真实
 * filter 挂进 MockMvc 过滤器链，覆盖 fail-closed（平台未配置令牌一律拒绝）与
 * ROBOTEST-CI-TOKEN 头放行；filter 单元语义另见 CiTokenAuthFilterTest。
 */
class CiBatchControllerApiTest {

    private static final String CI_TOKEN = "ci-token-xyz";
    private static final String BATCH_ID = "3f6a1c9e-8a4c-4d3e-9a1b-0b2c4d5e6f70";

    private MockMvc mockMvc;
    private ApiGitBatchService apiGitBatchService;
    private ApiGitCompletionService apiGitCompletionService;

    @BeforeEach
    void setUp() {
        apiGitBatchService = mock(ApiGitBatchService.class);
        apiGitCompletionService = mock(ApiGitCompletionService.class);

        GlobalExceptionHandler advice = new GlobalExceptionHandler(
                "robotest",
                mock(ApiErrorLogFrameworkService.class),
                new I18NMessage(new StaticMessageSource()));

        mockMvc = mockMvc(new CiTokenAuthFilter(CI_TOKEN), advice);
    }

    private MockMvc mockMvc(CiTokenAuthFilter filter, GlobalExceptionHandler advice) {
        CiBatchController controller = new CiBatchController();
        ReflectionTestUtils.setField(controller, "apiGitBatchService", apiGitBatchService);
        ReflectionTestUtils.setField(controller, "apiGitCompletionService", apiGitCompletionService);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .addFilters(filter)
                .build();
    }

    private ApiGitBatchManifestRespDTO manifestFixture() {
        ApiGitBatchManifestRespDTO resp = new ApiGitBatchManifestRespDTO();
        resp.setBatchId(BATCH_ID);
        resp.setRef("master");
        resp.setScenes(List.of(new ApiGitBatchManifestRespDTO.SceneItem(
                "scene-1", "com.qa.OrderApiTest#createOrder", 1)));
        resp.setExpectedCases(1);
        return resp;
    }

    // ========== 只读令牌鉴权（fail-closed） ==========

    @Test
    void manifest_withoutToken_rejected401() throws Exception {
        mockMvc.perform(get("/api/ci/batches/" + BATCH_ID + "/manifest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(apiGitBatchService, never()).getManifest(any());
    }

    @Test
    void manifest_wrongToken_rejected401() throws Exception {
        mockMvc.perform(get("/api/ci/batches/" + BATCH_ID + "/manifest")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, "tampered"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(apiGitBatchService, never()).getManifest(any());
    }

    @Test
    void manifest_unconfiguredPlatformToken_rejectsEvenWithMatchingHeader() throws Exception {
        // 平台侧 ci-token 未配置（空串）时 CI 端点整体不可用，避免匿名读取批次清单
        GlobalExceptionHandler advice = new GlobalExceptionHandler(
                "robotest",
                mock(ApiErrorLogFrameworkService.class),
                new I18NMessage(new StaticMessageSource()));
        MockMvc unconfigured = mockMvc(new CiTokenAuthFilter("  "), advice);

        unconfigured.perform(get("/api/ci/batches/" + BATCH_ID + "/manifest")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, "anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(apiGitBatchService, never()).getManifest(any());
    }

    // ========== 清单快照 ==========

    @Test
    void manifest_correctToken_returnsFrozenSnapshotEnvelope() throws Exception {
        when(apiGitBatchService.getManifest(BATCH_ID)).thenReturn(manifestFixture());

        mockMvc.perform(get("/api/ci/batches/" + BATCH_ID + "/manifest")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.batchId").value(BATCH_ID))
                .andExpect(jsonPath("$.data.ref").value("master"))
                .andExpect(jsonPath("$.data.expectedCases").value(1))
                .andExpect(jsonPath("$.data.scenes[0].sourceRef").value("com.qa.OrderApiTest#createOrder"))
                .andExpect(jsonPath("$.data.scenes[0].expectedCaseCount").value(1));

        verify(apiGitBatchService).getManifest(BATCH_ID);
    }

    @Test
    void manifest_unknownBatch_mapsTo1000017713Envelope() throws Exception {
        when(apiGitBatchService.getManifest(BATCH_ID))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND));

        mockMvc.perform(get("/api/ci/batches/" + BATCH_ID + "/manifest")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND.msg()));
    }

    // ========== 完成回报 ==========

    @Test
    void completion_correctToken_reportsCountsAndReturnsOk() throws Exception {
        mockMvc.perform(post("/api/ci/batches/" + BATCH_ID + "/completion")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN)
                        .contentType("application/json")
                        .content("{\"status\":\"success\",\"expectedCases\":5,\"actualCases\":5,"
                                + "\"pipelineUrl\":\"https://gitlab.example.com/qa/order-api-tests/-/pipelines/9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()));

        ArgumentCaptor<ApiGitBatchCompletionReqDTO> captor =
                ArgumentCaptor.forClass(ApiGitBatchCompletionReqDTO.class);
        verify(apiGitCompletionService).reportCompletion(eq(BATCH_ID), captor.capture());
        assertEquals("success", captor.getValue().getStatus());
        assertEquals(5, captor.getValue().getExpectedCases());
        assertEquals(5, captor.getValue().getActualCases());
    }

    @Test
    void completion_withoutToken_neverReachesService() throws Exception {
        mockMvc.perform(post("/api/ci/batches/" + BATCH_ID + "/completion")
                        .contentType("application/json")
                        .content("{\"status\":\"success\",\"expectedCases\":1,\"actualCases\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(apiGitCompletionService, never()).reportCompletion(any(), any());
    }

    @Test
    void completion_missingStatus_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/ci/batches/" + BATCH_ID + "/completion")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN)
                        .contentType("application/json")
                        .content("{\"expectedCases\":1,\"actualCases\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.msg", containsString("回报状态不能为空")));

        verify(apiGitCompletionService, never()).reportCompletion(any(), any());
    }

    @Test
    void completion_negativeActualCases_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/ci/batches/" + BATCH_ID + "/completion")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN)
                        .contentType("application/json")
                        .content("{\"status\":\"failed\",\"expectedCases\":1,\"actualCases\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.msg", containsString("实际用例数不能为负数")));

        verify(apiGitCompletionService, never()).reportCompletion(any(), any());
    }

    @Test
    void completion_unknownBatch_mapsTo1000017713Envelope() throws Exception {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND))
                .when(apiGitCompletionService).reportCompletion(eq(BATCH_ID), any());

        mockMvc.perform(post("/api/ci/batches/" + BATCH_ID + "/completion")
                        .header(CiTokenAuthFilter.TOKEN_HEADER, CI_TOKEN)
                        .contentType("application/json")
                        .content("{\"status\":\"failed\",\"expectedCases\":1,\"actualCases\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_BATCH_NOT_FOUND.msg()));
    }
}
