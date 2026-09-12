package io.github.xiaomisum.robotest.service.apitest;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleValidateCronReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleCreatedRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecuteNowRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecutionItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSchedulePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleValidateCronRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiImportRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTaskExecution;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 定时任务管理核心路径（定时任务详细设计 3.1、4.1） */
@ExtendWith(MockitoExtension.class)
class ApiScheduleServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();

    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ProjectModuleMapper moduleMapper;
    @Mock
    private ApiImportRecordMapper importRecordMapper;
    @Mock
    private ImportSourceFetcher importSourceFetcher;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ScheduledTaskRunner taskRunner;
    @Mock
    private ApiTestTaskScheduler taskScheduler;

    @InjectMocks
    private ApiScheduleServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // wrapper 显式置 null（C9）需要 MyBatis-Plus 的 lambda 列缓存，纯单测环境下手动初始化；
        // UUID 主键列还需注册框架 UUIDTypeHandler，否则 TableInfo 构建失败
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class,
                xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), ApiScheduledTask.class);
    }

    // ========== 创建 ==========

    @Test
    void createRejectsInvalidCron() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "not a cron");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017502, ex.getCode().intValue());
    }

    @Test
    void createTestPlanTaskRequiresEnvironment() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "0 2 * * *");
        reqDTO.setEnvironmentId(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017503, ex.getCode().intValue());
    }

    @Test
    void createTestPlanTaskRejectsForeignEnvironment() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("all", "0 2 * * *");
        ApiEnvironment foreign = new ApiEnvironment();
        foreign.setId(reqDTO.getEnvironmentId());
        foreign.setProjectId(UUID.randomUUID());
        when(environmentMapper.selectById(reqDTO.getEnvironmentId())).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017405, ex.getCode().intValue());
    }

    @Test
    void createScenesScopeRejectsSceneWithoutEnabledSteps() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("scenes", "0 2 * * *");
        UUID sceneId = UUID.randomUUID();
        reqDTO.setSceneIds(List.of(sceneId));
        stubValidEnvironment(reqDTO);
        ApiScene scene = sceneWithSteps(sceneId, 0);
        when(sceneMapper.selectByIds(List.of(sceneId))).thenReturn(List.of(scene));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017505, ex.getCode().intValue());
    }

    @Test
    void createScenesScopeRejectsForeignScene() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("scenes", "0 2 * * *");
        UUID sceneId = UUID.randomUUID();
        reqDTO.setSceneIds(List.of(sceneId));
        stubValidEnvironment(reqDTO);
        ApiScene foreign = sceneWithSteps(sceneId, 1);
        foreign.setProjectId(UUID.randomUUID());
        when(sceneMapper.selectByIds(List.of(sceneId))).thenReturn(List.of(foreign));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017301, ex.getCode().intValue());
    }

    @Test
    void createScenesScopeRejectsMissingScene() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("scenes", "0 2 * * *");
        UUID sceneId = UUID.randomUUID();
        reqDTO.setSceneIds(List.of(sceneId));
        stubValidEnvironment(reqDTO);
        when(sceneMapper.selectByIds(List.of(sceneId))).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017301, ex.getCode().intValue());
    }

    @Test
    void createModulesScopeRejectsForeignModule() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("modules", "0 2 * * *");
        UUID moduleId = UUID.randomUUID();
        reqDTO.setModuleIds(List.of(moduleId));
        stubValidEnvironment(reqDTO);
        ProjectModule foreign = new ProjectModule();
        foreign.setId(moduleId);
        foreign.setProjectId(UUID.randomUUID());
        when(moduleMapper.listByIds(List.of(moduleId))).thenReturn(List.of(foreign));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017509, ex.getCode().intValue());
    }

    @Test
    void createSyncTaskRequiresOpenapiUrl() {
        ApiScheduleSaveReqDTO reqDTO = syncReq("0 * * * *", null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017510, ex.getCode().intValue());
    }

    @Test
    void createSyncTaskRejectsUnreachableUrl() {
        ApiScheduleSaveReqDTO reqDTO = syncReq("0 * * * *", "https://example.com/v3/api-docs");
        when(importSourceFetcher.fetch("https://example.com/v3/api-docs"))
                .thenThrow(new ServiceException(1000017012, "超时"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017012, ex.getCode().intValue());
    }

    @Test
    void createTestPlanTaskPersistsScopeAndNotifiesScheduler() {
        ApiScheduleSaveReqDTO reqDTO = testPlanReq("all", "0 2 * * *");
        stubValidEnvironment(reqDTO);

        ApiScheduleCreatedRespDTO resp = service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        assertNotNull(resp.getId());
        assertNotNull(resp.getNextExecutionAt());

        ArgumentCaptor<ApiScheduledTask> captor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).insert(captor.capture());
        ApiScheduledTask inserted = captor.getValue();
        assertEquals("scene_execute", inserted.getTaskType());
        assertEquals("all", inserted.getExecutionScope());
        assertEquals(reqDTO.getEnvironmentId(), inserted.getEnvironmentId());
        assertEquals(Boolean.TRUE, inserted.getEnabled());
        assertEquals(PROJECT_ID, inserted.getProjectId());
        // 非对应类型字段不落库
        assertNull(inserted.getSceneIds());
        assertNull(inserted.getModuleIds());
        assertNull(inserted.getOpenapiUrl());
        verify(taskScheduler).onTaskChanged(inserted.getId());
    }

    @Test
    void createSyncTaskPersistsTrimmedUrl() {
        ApiScheduleSaveReqDTO reqDTO = syncReq("0 * * * *", "  https://example.com/v3/api-docs  ");
        when(importSourceFetcher.fetch("https://example.com/v3/api-docs")).thenReturn("{}");

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<ApiScheduledTask> captor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).insert(captor.capture());
        ApiScheduledTask inserted = captor.getValue();
        assertEquals("https://example.com/v3/api-docs", inserted.getOpenapiUrl());
        // 接口同步任务不落 scene_execute 字段
        assertNull(inserted.getExecutionScope());
        assertNull(inserted.getEnvironmentId());
    }

    // ========== 更新/启停/删除 ==========

    @Test
    void updateRequiresExistingTask() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, testPlanReq("all", "0 2 * * *")));
        assertEquals(1000017501, ex.getCode().intValue());
    }

    @Test
    void updateSwitchingToSyncClearsTestPlanFields() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());
        ApiScheduleSaveReqDTO reqDTO = syncReq("0 * * * *", "https://example.com/v3/api-docs");
        when(importSourceFetcher.fetch(reqDTO.getOpenapiUrl())).thenReturn("{}");

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, reqDTO);

        // C9 部分更新：类型切换时废弃字段显式置 null，必须走 wrapper 更新而非 updateById
        verify(taskMapper).update(eq(null), any());
        verify(taskScheduler).onTaskChanged(TASK_ID);
    }

    @Test
    void toggleWritesEnabledAndNotifiesScheduler() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());

        ApiScheduleToggleReqDTO reqDTO = new ApiScheduleToggleReqDTO();
        reqDTO.setEnabled(false);
        service.toggle(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, reqDTO);

        // 启停只改 enabled 一列（C9 部分更新），载体为 wrapper
        verify(taskMapper).update(eq(null), any());
        verify(taskScheduler).onTaskChanged(TASK_ID);
    }

    @Test
    void deleteRemovesTaskAndNotifiesScheduler() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());

        service.delete(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID);

        verify(taskMapper).deleteById(TASK_ID);
        verify(taskScheduler).onTaskChanged(TASK_ID);
    }

    // ========== 立即执行 ==========

    @Test
    void executeNowRejectsRunningTask() {
        ApiScheduledTask task = existingTask();
        task.setLastExecutionStatus("running");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.executeNow(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID));
        assertEquals(1000017504, ex.getCode().intValue());
    }

    @Test
    void executeNowTestPlanReturnsRunningAndLaunchesBatch() {
        ApiScheduledTask task = existingTask();
        task.setLastExecutionStatus("success");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);

        ApiScheduleExecuteNowRespDTO resp =
                service.executeNow(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID);

        verify(taskRunner).launchTestPlanAsyncFinalize(task, USER_ID, "manual");
        assertEquals(TASK_ID, resp.getExecutionId());
        assertEquals("running", resp.getStatus());
    }

    @Test
    void executeNowSyncReturnsFinalOutcome() {
        ApiScheduledTask task = existingTask();
        task.setTaskType("import_swagger");
        task.setLastExecutionStatus(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        UUID importId = UUID.randomUUID();
        when(taskRunner.runSyncRethrow(task, USER_ID, "manual"))
                .thenReturn(new ScheduledTaskRunner.ImportOutcome(importId, "success"));

        ApiScheduleExecuteNowRespDTO resp =
                service.executeNow(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID);

        assertEquals(importId, resp.getExecutionId());
        assertEquals("success", resp.getStatus());
    }

    // ========== 执行记录 ==========

    @Test
    void executionsEnrichesImportSummary() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());
        UUID importId = UUID.randomUUID();
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTriggerType("scheduled");
        record.setStatus("success");
        record.setImportRecordId(importId);
        when(executionMapper.selectPageByTask(eq(TASK_ID), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(record), 1L));
        ApiImportRecord importRecord = new ApiImportRecord();
        importRecord.setId(importId);
        importRecord.setSummary(Map.of("created", 3));
        when(importRecordMapper.selectByIds(List.of(importId))).thenReturn(List.of(importRecord));

        PageResult<ApiScheduleExecutionItemRespDTO> page =
                service.executions(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, new PageParam());

        assertEquals(1, page.getList().size());
        assertEquals(Map.of("created", 3), page.getList().get(0).getImportSummary());
        assertEquals("scheduled", page.getList().get(0).getTriggerType());
    }

    @Test
    void executionsWithoutImportRecordIdDoNotThrow() {
        // 测试计划类型（scene_execute）执行记录不写 importRecordId（ScheduledTaskRunner 传 null），
        // 全页无导入记录时 importSummaries 为不可变空 Map，MapN.get(null) 会 NPE——必须产出空摘要而非报错
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());
        ApiScheduledTaskExecution record = new ApiScheduledTaskExecution();
        record.setId(UUID.randomUUID());
        record.setTriggerType("scheduled");
        record.setStatus("success");
        record.setImportRecordId(null);
        when(executionMapper.selectPageByTask(eq(TASK_ID), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(record), 1L));

        PageResult<ApiScheduleExecutionItemRespDTO> page =
                service.executions(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, new PageParam());

        assertEquals(1, page.getList().size());
        assertNull(page.getList().get(0).getImportSummary());
        assertNull(page.getList().get(0).getImportRecordId());
    }

    // ========== Cron 校验与列表预览 ==========

    @Test
    void validateCronReturnsPresetDescriptionAndFiveTimes() {
        ApiScheduleValidateCronReqDTO reqDTO = new ApiScheduleValidateCronReqDTO();
        reqDTO.setCronExpression("0 2 * * *");

        ApiScheduleValidateCronRespDTO resp = service.validateCron(reqDTO);

        assertTrue(resp.isValid());
        assertEquals("每天凌晨 2:00", resp.getDescription());
        assertEquals(5, resp.getNextExecutions().size());
    }

    @Test
    void validateCronMarksInvalidWithoutTimes() {
        ApiScheduleValidateCronReqDTO reqDTO = new ApiScheduleValidateCronReqDTO();
        reqDTO.setCronExpression("* * *");

        ApiScheduleValidateCronRespDTO resp = service.validateCron(reqDTO);

        assertFalse(resp.isValid());
        assertNull(resp.getDescription());
        assertNull(resp.getNextExecutions());
    }

    @Test
    void validateCronNormalizesTimesToUtc() {
        ApiScheduleValidateCronReqDTO reqDTO = new ApiScheduleValidateCronReqDTO();
        reqDTO.setCronExpression("0 2 * * *");

        ApiScheduleValidateCronRespDTO resp = service.validateCron(reqDTO);

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        // 归一化为 UTC 钟面后，所有触发时刻均应晚于当前 UTC 时刻（未归一化的本地钟面在 UTC 环境会早）
        assertTrue(resp.getNextExecutions().stream().allMatch(t -> t.isAfter(nowUtc)));
        // 与服务器本地钟面换算回 UTC 的结果完全一致（任意 JVM 时区下均成立）
        List<LocalDateTime> expected = CronSupport.nextN("0 2 * * *", LocalDateTime.now(), 5).stream()
                .map(t -> t.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .toList();
        assertEquals(expected, resp.getNextExecutions());
    }

    @Test
    void pageHidesNextExecutionsForDisabledTasks() {
        ApiScheduledTask disabled = existingTask();
        disabled.setEnabled(false);
        when(taskMapper.selectPage(any(PageParam.class), any()))
                .thenReturn(new PageResult<>(List.of(disabled), 1L));

        PageResult<ApiSchedulePageItemRespDTO> page =
                service.page(WORKSPACE_ID, PROJECT_ID, USER_ID, null, new PageParam());

        assertTrue(page.getList().get(0).getNextExecutions().isEmpty());
    }

    @Test
    void pageFillsEnvironmentNameAndNextExecutions() {
        ApiScheduledTask enabled = existingTask();
        enabled.setEnabled(true);
        enabled.setCronExpression("0 2 * * *");
        UUID envId = enabled.getEnvironmentId();
        when(taskMapper.selectPage(any(PageParam.class), any()))
                .thenReturn(new PageResult<>(List.of(enabled), 1L));
        ApiEnvironment env = new ApiEnvironment();
        env.setId(envId);
        env.setName("测试环境");
        when(environmentMapper.selectBatchIds(List.of(envId))).thenReturn(List.of(env));

        PageResult<ApiSchedulePageItemRespDTO> page =
                service.page(WORKSPACE_ID, PROJECT_ID, USER_ID, "scene_execute", new PageParam());

        ApiSchedulePageItemRespDTO item = page.getList().get(0);
        assertEquals("测试环境", item.getEnvironmentName());
        assertFalse(item.getNextExecutions().isEmpty());
        assertTrue(item.getNextExecutions().size() <= 3);
    }

    // ========== 夹具 ==========

    private ApiScheduleSaveReqDTO baseReq(String taskType, String cron) {
        ApiScheduleSaveReqDTO reqDTO = new ApiScheduleSaveReqDTO();
        reqDTO.setTaskType(taskType);
        reqDTO.setName("夜间回归");
        reqDTO.setCronExpression(cron);
        return reqDTO;
    }

    private ApiScheduleSaveReqDTO testPlanReq(String scope, String cron) {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", cron);
        reqDTO.setExecutionScope(scope);
        reqDTO.setEnvironmentId(UUID.randomUUID());
        return reqDTO;
    }

    private ApiScheduleSaveReqDTO syncReq(String cron, String openapiUrl) {
        ApiScheduleSaveReqDTO reqDTO = baseReq("import_swagger", cron);
        reqDTO.setOpenapiUrl(openapiUrl);
        return reqDTO;
    }

    private void stubValidEnvironment(ApiScheduleSaveReqDTO reqDTO) {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(reqDTO.getEnvironmentId());
        env.setProjectId(PROJECT_ID);
        when(environmentMapper.selectById(reqDTO.getEnvironmentId())).thenReturn(env);
    }

    private ApiScene sceneWithSteps(UUID sceneId, int enabledSteps) {
        ApiScene scene = new ApiScene();
        scene.setId(sceneId);
        scene.setProjectId(PROJECT_ID);
        java.util.ArrayList<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < enabledSteps; i++) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("id", UUID.randomUUID());
            step.put("enabled", true);
            steps.add(step);
        }
        scene.setSteps(steps);
        return scene;
    }

    private ApiScheduledTask existingTask() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("scene_execute");
        task.setName("夜间回归");
        task.setExecutionScope("all");
        task.setEnvironmentId(UUID.randomUUID());
        task.setCronExpression("*/5 * * * *");
        task.setEnabled(true);
        return task;
    }
}