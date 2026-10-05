package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiArtifactConfirm;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.repository.ai.AiArtifactConfirmMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskServiceImplTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID MODEL_ID = UUID.randomUUID();
    private static final String TYPE = "requirement_split";

    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiArtifactConfirmMapper confirmMapper;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private TaskHandlerRegistry handlerRegistry;
    @Mock
    private ArtifactAdopterRegistry adopterRegistry;
    @Mock
    private AiTaskExecutor taskExecutor;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TaskHandler handler;

    @InjectMocks
    private AiTaskServiceImpl service;

    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setAuthorities(List.of(new SimpleGrantedAuthority("ai:task"),
                new SimpleGrantedAuthority("ai:confirm")));
        loginUser.setWorkspaceAuthorities(new ArrayList<>());
        loginUser.setActiveWorkspaceId(WORKSPACE_ID);
        loginUser.setActiveProjectId(PROJECT_ID);

        lenient().when(settingsReader.settings())
                .thenReturn(new AiSettingsReader.AiSettings(true, MODEL_ID, 600, 2));
        lenient().when(settingsReader.usableModel()).thenReturn(model());
        lenient().when(handlerRegistry.get(TYPE)).thenReturn(handler);
        // 纯单测无 MP 主键填充：插入桩补写 id，保证后续按 id 流转
        lenient().doAnswer(invocation -> {
            AiTask entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return 1;
        }).when(taskMapper).insert(any(AiTask.class));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private static AiModelConfig model() {
        AiModelConfig model = new AiModelConfig();
        model.setId(MODEL_ID);
        model.setEnabled(true);
        model.setModelName("gpt-x");
        model.setBaseUrl("http://localhost:11434/v1");
        return model;
    }

    private static AiTaskSubmitReqDTO submitReq(Integer waitSeconds) {
        AiTaskSubmitReqDTO req = new AiTaskSubmitReqDTO();
        req.setType(TYPE);
        req.setInput(Map.of("requirementId", UUID.randomUUID().toString()));
        req.setWaitSeconds(waitSeconds);
        return req;
    }

    private static AiTask task(String status) {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setType(TYPE);
        task.setStatus(status);
        task.setProgress(0);
        task.setProjectId(PROJECT_ID);
        task.setWorkspaceId(WORKSPACE_ID);
        task.setSubmittedBy(USER_ID);
        task.setPromptScene(TYPE);
        task.setModelId(MODEL_ID);
        task.setInput(Map.of());
        return task;
    }

    /** HTTP 提交的上下文参数取自请求头解析后的 LoginUser（C4），与控制器装配口径一致 */
    private AiTaskRespDTO submit(AiTaskSubmitReqDTO req) {
        return service.submit(req, PROJECT_ID, WORKSPACE_ID, USER_ID, loginUser);
    }

    // ---------- 提交（3.6.2 校验顺序：等待参数 → 总开关 → 权限 → 类型 → 输入 → 模型） ----------

    @Test
    void submit_waitSecondsInvalid_throws123() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(11)));
        assertEquals(ErrorCodeConstants.AI_WAIT_SECONDS_INVALID.code(), exception.getCode());
    }

    @Test
    void submit_switchOff_throws101() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(0)));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
        verifyNoInteractions(taskMapper);
    }

    @Test
    void submit_noAiTaskPermission_throws116() {
        loginUser.setAuthorities(List.of());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(0)));
        assertEquals(ErrorCodeConstants.AI_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void submit_unregisteredType_throws114() {
        when(handlerRegistry.get(TYPE)).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(0)));
        assertEquals(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(), exception.getCode());
    }

    @Test
    void submit_invalidInput_throws115() {
        doThrow(ServiceExceptionUtilGet(ErrorCodeConstants.AI_TASK_INPUT_INVALID))
                .when(handler).validateInput(any(), any());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(0)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(taskExecutor, never()).start(any());
    }

    @Test
    void submit_noUsableModel_throws118() {
        when(settingsReader.usableModel()).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> submit(submitReq(0)));
        assertEquals(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED.code(), exception.getCode());
        verify(taskExecutor, never()).start(any());
    }

    @Test
    void submit_success_insertsPendingAndStarts() {
        AiTaskRespDTO resp = submit(submitReq(0));

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).insert(captor.capture());
        AiTask inserted = captor.getValue();
        assertEquals(TYPE, inserted.getType());
        assertEquals("pending", inserted.getStatus());
        assertEquals(TYPE, inserted.getPromptScene());
        assertEquals(MODEL_ID, inserted.getModelId());
        assertEquals(PROJECT_ID, inserted.getProjectId());
        assertEquals(WORKSPACE_ID, inserted.getWorkspaceId());
        assertEquals(USER_ID, inserted.getSubmittedBy());
        assertEquals("pending", resp.getStatus());
        assertNull(resp.getResult());
        verify(taskExecutor).start(inserted.getId());
    }

    @Test
    void submit_withWaitCompleted_returnsResultAndArtifacts() {
        // 本用例需插入 id 与轮询桩一致
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, AiTask.class).setId(TASK_ID);
            return 1;
        }).when(taskMapper).insert(any(AiTask.class));
        AiTask done = task("succeeded");
        done.setResult(Map.of("artifacts", List.of(
                Map.of("key", "a1", "kind", "requirement_suggestion", "title", "需求A"))));
        when(taskMapper.selectById(TASK_ID)).thenReturn(done);
        when(confirmMapper.selectByTask(TASK_ID)).thenReturn(List.of());

        AiTaskRespDTO resp = submit(submitReq(5));

        assertEquals("succeeded", resp.getStatus());
        assertNotNull(resp.getResult());
        assertNotNull(resp.getArtifacts());
        assertEquals("a1", resp.getArtifacts().get(0).getKey());
        assertEquals("pending", resp.getArtifacts().get(0).getConfirmStatus());
    }

    @Test
    void submitInternal_switchOff_throws101() {
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.submitInternal("impact_analysis", Map.of("requirementId", "x"),
                        USER_ID, PROJECT_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
    }

    // ---------- 列表与详情（3.6.3） ----------

    @Test
    void page_withProjectContext_filtersByProject() {
        AiTaskPageReqDTO pageReq = new AiTaskPageReqDTO();
        pageReq.setType(TYPE);
        when(taskMapper.findPage(pageReq, PROJECT_ID, USER_ID, TYPE, null))
                .thenReturn(new PageResult<>(List.of(task("pending")), 1L));

        PageResult<AiTaskRespDTO> page = service.page(pageReq, PROJECT_ID, USER_ID);

        assertEquals(1, page.getTotal());
        assertEquals("pending", page.getList().get(0).getStatus());
    }

    @Test
    void page_withoutProjectContext_returnsOwnTasks() {
        AiTaskPageReqDTO pageReq = new AiTaskPageReqDTO();
        when(taskMapper.findPage(pageReq, null, USER_ID, null, null))
                .thenReturn(new PageResult<>(List.of(), 0L));

        PageResult<AiTaskRespDTO> page = service.page(pageReq, null, USER_ID);

        assertEquals(0, page.getTotal());
    }

    @Test
    void detail_foreignProjectAndNotOwner_throws110() {
        AiTask foreign = task("pending");
        foreign.setProjectId(UUID.randomUUID());
        foreign.setSubmittedBy(OTHER_ID);
        when(taskMapper.selectById(TASK_ID)).thenReturn(foreign);
        loginUser.setActiveProjectId(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void detail_succeeded_attachesArtifactSummariesWithConfirmStatus() {
        AiTask done = task("succeeded");
        done.setResult(Map.of("artifacts", List.of(
                Map.of("key", "a1", "kind", "requirement_suggestion", "title", "需求A", "parentKey", "root"),
                Map.of("key", "a2", "kind", "requirement_suggestion", "title", "需求B"))));
        AiArtifactConfirm confirmed = new AiArtifactConfirm();
        confirmed.setArtifactKey("a1");
        confirmed.setAction("adopted");
        when(taskMapper.selectById(TASK_ID)).thenReturn(done);
        when(confirmMapper.selectByTask(TASK_ID)).thenReturn(List.of(confirmed));

        AiTaskRespDTO resp = service.getDetail(TASK_ID, loginUser);

        assertNull(resp.getResult());
        assertEquals(2, resp.getArtifacts().size());
        assertEquals("adopted", resp.getArtifacts().get(0).getConfirmStatus());
        assertEquals("pending", resp.getArtifacts().get(1).getConfirmStatus());
        assertEquals("root", resp.getArtifacts().get(0).getParentKey());
    }

    @Test
    void detail_failed_carriesError() {
        AiTask failed = task("failed");
        failed.setErrorCode(1000018117);
        failed.setErrorMsg("模型服务调用失败");
        when(taskMapper.selectById(TASK_ID)).thenReturn(failed);

        AiTaskRespDTO resp = service.getDetail(TASK_ID, loginUser);

        assertEquals(1000018117, resp.getError().getCode());
        assertEquals("模型服务调用失败", resp.getError().getMsg());
    }

    @Test
    void artifactDetail_found_returnsFullArtifact() {
        Map<String, Object> artifact = Map.of("key", "a1", "kind", "k", "content", Map.of("title", "T"));
        AiTask done = task("succeeded");
        done.setResult(Map.of("artifacts", List.of(artifact)));
        when(taskMapper.selectById(TASK_ID)).thenReturn(done);

        Map<String, Object> result = service.getArtifact(TASK_ID, "a1", loginUser);

        assertEquals(Map.of("title", "T"), result.get("content"));
    }

    @Test
    void artifactDetail_missing_throws112() {
        AiTask done = task("succeeded");
        done.setResult(Map.of("artifacts", List.of(Map.of("key", "a1"))));
        when(taskMapper.selectById(TASK_ID)).thenReturn(done);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getArtifact(TASK_ID, "nope", loginUser));
        assertEquals(ErrorCodeConstants.AI_ARTIFACT_NOT_FOUND.code(), exception.getCode());
    }

    // ---------- 取消 / 重试（3.6.4） ----------

    @Test
    void cancel_pending_succeedsAndAudits() {
        AiTask cancelled = task("cancelled");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("pending"), cancelled);
        when(taskMapper.casCancel(TASK_ID)).thenReturn(true);

        AiTaskRespDTO resp = service.cancel(TASK_ID, loginUser);

        assertEquals("cancelled", resp.getStatus());
        verify(taskMapper).casCancel(TASK_ID);
    }

    @Test
    void cancel_terminalState_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("succeeded"));
        when(taskMapper.casCancel(TASK_ID)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.cancel(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
    }

    @Test
    void cancel_withoutAiTaskPermission_throws116() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("pending"));
        loginUser.setAuthorities(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.cancel(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_NO_PERMISSION.code(), exception.getCode());
        verify(taskMapper, never()).casCancel(any());
    }

    @Test
    void retry_failed_copiesInputAndModelWithRetryLink() {
        AiTask failed = task("failed");
        failed.setInput(Map.of("requirementId", "r-1"));
        when(taskMapper.selectById(TASK_ID)).thenReturn(failed);

        AiTaskRespDTO resp = service.retry(TASK_ID, loginUser);

        ArgumentCaptor<AiTask> captor = ArgumentCaptor.forClass(AiTask.class);
        verify(taskMapper).insert(captor.capture());
        AiTask created = captor.getValue();
        assertEquals("pending", created.getStatus());
        assertEquals(TASK_ID, created.getRetryOfTaskId());
        assertEquals(MODEL_ID, created.getModelId());
        assertEquals(Map.of("requirementId", "r-1"), created.getInput());
        assertEquals(USER_ID, created.getSubmittedBy());
        assertEquals("pending", resp.getStatus());
        verify(taskExecutor).start(created.getId());
    }

    @Test
    void retry_nonFailed_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("succeeded"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.retry(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
        verify(taskMapper, never()).insert(any(AiTask.class));
    }

    @Test
    void retry_switchOff_throws101() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("failed"));
        when(settingsReader.settings()).thenReturn(new AiSettingsReader.AiSettings(false, MODEL_ID, 600, 2));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.retry(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
    }

    // ---------- 产物确认（3.6.5：整体 200、逐项成败） ----------

    private AiTaskConfirmReqDTO confirmReq(String key, String action) {
        AiTaskConfirmReqDTO req = new AiTaskConfirmReqDTO();
        AiTaskConfirmReqDTO.ConfirmItemReqDTO item = new AiTaskConfirmReqDTO.ConfirmItemReqDTO();
        item.setKey(key);
        item.setAction(action);
        req.setItems(List.of(item));
        return req;
    }

    private AiTask succeededWithArtifacts() {
        AiTask done = task("succeeded");
        done.setResult(Map.of("artifacts", List.of(Map.of("key", "a1", "kind", "k", "title", "T"))));
        return done;
    }

    @Test
    void confirm_withoutConfirmPermission_throws116() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        loginUser.setAuthorities(List.of(new SimpleGrantedAuthority("ai:task")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser));
        assertEquals(ErrorCodeConstants.AI_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void confirm_taskNotSucceeded_throws111() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task("running"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_STATE_INVALID.code(), exception.getCode());
    }

    @Test
    void confirm_adopted_insertsConfirmRecordAndReturnsCreatedId() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(null);
        ArtifactAdopter adopter = org.mockito.Mockito.mock(ArtifactAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        UUID createdId = UUID.randomUUID();
        when(adopter.adopt(any())).thenReturn(new AdoptOutcome(createdId, Map.of("id", createdId)));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser);

        assertTrue(resp.getResults().get(0).getSuccess());
        assertEquals(createdId, resp.getResults().get(0).getCreatedId());
        ArgumentCaptor<AiArtifactConfirm> captor = ArgumentCaptor.forClass(AiArtifactConfirm.class);
        verify(confirmMapper).insert(captor.capture());
        assertEquals(TASK_ID, captor.getValue().getTaskId());
        assertEquals("a1", captor.getValue().getArtifactKey());
        assertEquals("adopted", captor.getValue().getAction());
        assertEquals(PROJECT_ID, captor.getValue().getProjectId());
        assertEquals(USER_ID, captor.getValue().getOperatorId());
    }

    @Test
    void confirm_artifactMissing_itemFails112() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("nope", "adopted"), loginUser);

        AiTaskConfirmRespDTO.ItemResult item = resp.getResults().get(0);
        assertEquals(false, item.getSuccess());
        assertEquals(ErrorCodeConstants.AI_ARTIFACT_NOT_FOUND.code(), item.getErrorCode());
        verify(confirmMapper, never()).insert(any(AiArtifactConfirm.class));
    }

    @Test
    void confirm_invalidAction_itemFails115() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "boom"), loginUser);

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), resp.getResults().get(0).getErrorCode());
    }

    @Test
    void confirm_alreadyConfirmed_itemFails113() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(new AiArtifactConfirm());

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "rejected"), loginUser);

        assertEquals(ErrorCodeConstants.AI_ARTIFACT_ALREADY_CONFIRMED.code(),
                resp.getResults().get(0).getErrorCode());
        verify(confirmMapper, never()).insert(any(AiArtifactConfirm.class));
    }

    @Test
    void confirm_adopterNotRegistered_itemFails114() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(null);
        when(adopterRegistry.get(TYPE)).thenReturn(null);

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser);

        assertEquals(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(), resp.getResults().get(0).getErrorCode());
    }

    @Test
    void confirm_adopterThrows_itemFailsWithoutInsert() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(null);
        ArtifactAdopter adopter = org.mockito.Mockito.mock(ArtifactAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        doThrow(ServiceExceptionUtilGet(ErrorCodeConstants.AI_TASK_INPUT_INVALID))
                .when(adopter).adopt(any());

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser);

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), resp.getResults().get(0).getErrorCode());
        verify(confirmMapper, never()).insert(any(AiArtifactConfirm.class));
    }

    @Test
    void confirm_uniqueRace_itemFails113() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(succeededWithArtifacts());
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(null);
        ArtifactAdopter adopter = org.mockito.Mockito.mock(ArtifactAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        when(adopter.adopt(any())).thenReturn(new AdoptOutcome(null, null));
        doThrow(new DuplicateKeyException("uk")).when(confirmMapper).insert(any(AiArtifactConfirm.class));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, confirmReq("a1", "adopted"), loginUser);

        assertEquals(ErrorCodeConstants.AI_ARTIFACT_ALREADY_CONFIRMED.code(),
                resp.getResults().get(0).getErrorCode());
    }

    @Test
    void confirm_mixedItems_eachSucceedsOrFailsIndependently() {
        AiTask done = succeededWithArtifacts();
        when(taskMapper.selectById(TASK_ID)).thenReturn(done);
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "a1")).thenReturn(null);
        ArtifactAdopter adopter = org.mockito.Mockito.mock(ArtifactAdopter.class);
        when(adopterRegistry.get(TYPE)).thenReturn(adopter);
        when(adopter.adopt(any())).thenReturn(new AdoptOutcome(UUID.randomUUID(), Map.of()));

        AiTaskConfirmReqDTO req = new AiTaskConfirmReqDTO();
        AiTaskConfirmReqDTO.ConfirmItemReqDTO ok = new AiTaskConfirmReqDTO.ConfirmItemReqDTO();
        ok.setKey("a1");
        ok.setAction("adopted_edited");
        ok.setContent(Map.of("title", "编辑后"));
        AiTaskConfirmReqDTO.ConfirmItemReqDTO bad = new AiTaskConfirmReqDTO.ConfirmItemReqDTO();
        bad.setKey("missing");
        bad.setAction("adopted");
        req.setItems(List.of(ok, bad));

        AiTaskConfirmRespDTO resp = service.confirm(TASK_ID, req, loginUser);

        assertEquals(2, resp.getResults().size());
        assertTrue(resp.getResults().get(0).getSuccess());
        assertEquals(false, resp.getResults().get(1).getSuccess());
        assertEquals(ErrorCodeConstants.AI_ARTIFACT_NOT_FOUND.code(), resp.getResults().get(1).getErrorCode());
    }

    @Test
    void visibility_ownTask_visibleEvenWithoutProjectContext() {
        AiTask own = task("pending");
        own.setProjectId(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(own);
        loginUser.setActiveProjectId(null);

        assertNotNull(service.getDetail(TASK_ID, loginUser));
    }

    @Test
    void requireVisible_missingTask_throws110() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(TASK_ID, loginUser));
        assertEquals(ErrorCodeConstants.AI_TASK_NOT_FOUND.code(), exception.getCode());
    }

    private static ServiceException ServiceExceptionUtilGet(xyz.migoo.framework.common.exception.ErrorCode code) {
        return xyz.migoo.framework.common.exception.ServiceExceptionUtil.get(code);
    }
}
