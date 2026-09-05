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
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

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
    private ApiSwaggerUrlMapper swaggerUrlMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ApiImportRecordMapper importRecordMapper;
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
    void createSceneTaskRequiresEnvironment() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "0 2 * * *");
        reqDTO.setEnvironmentId(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017503, ex.getCode().intValue());
    }

    @Test
    void createSceneTaskRejectsForeignEnvironment() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "0 2 * * *");
        ApiEnvironment foreign = new ApiEnvironment();
        foreign.setId(reqDTO.getEnvironmentId());
        foreign.setProjectId(UUID.randomUUID());
        when(environmentMapper.selectById(reqDTO.getEnvironmentId())).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017405, ex.getCode().intValue());
    }

    @Test
    void createSceneTaskRejectsSceneWithoutEnabledSteps() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "0 2 * * *");
        stubValidEnvironment(reqDTO);
        ApiScene scene = stubValidScene(reqDTO.getBoundObjectId(), 0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017505, ex.getCode().intValue());
        assertNotNull(scene.getName());
    }

    @Test
    void createImportTaskRejectsMissingSwaggerConfig() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("import_swagger", "*/5 * * * *");
        when(swaggerUrlMapper.selectById(reqDTO.getBoundObjectId())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017601, ex.getCode().intValue());
    }

    @Test
    void createSnapshotsBoundObjectNameAndNotifiesScheduler() {
        ApiScheduleSaveReqDTO reqDTO = baseReq("scene_execute", "0 2 * * *");
        stubValidEnvironment(reqDTO);
        ApiScene scene = stubValidScene(reqDTO.getBoundObjectId(), 2);

        ApiScheduleCreatedRespDTO resp = service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        assertNotNull(resp.getId());
        assertNotNull(resp.getNextExecutionAt());

        ArgumentCaptor<ApiScheduledTask> captor = ArgumentCaptor.forClass(ApiScheduledTask.class);
        verify(taskMapper).insert(captor.capture());
        ApiScheduledTask inserted = captor.getValue();
        assertEquals(scene.getName(), inserted.getBoundObjectName());
        assertEquals(Boolean.TRUE, inserted.getEnabled());
        assertEquals(PROJECT_ID, inserted.getProjectId());
        verify(taskScheduler).onTaskChanged(inserted.getId());
    }

    // ========== 更新/启停/删除 ==========

    @Test
    void updateRequiresExistingTask() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, baseReq("scene_execute", "0 2 * * *")));
        assertEquals(1000017501, ex.getCode().intValue());
    }

    @Test
    void updateSwitchingToImportClearsEnvironment() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(existingTask());
        ApiScheduleSaveReqDTO reqDTO = baseReq("import_swagger", "0 * * * *");
        ApiSwaggerUrl config = new ApiSwaggerUrl();
        config.setId(reqDTO.getBoundObjectId());
        config.setProjectId(PROJECT_ID);
        config.setName("生产 Swagger");
        when(swaggerUrlMapper.selectById(reqDTO.getBoundObjectId())).thenReturn(config);

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, reqDTO);

        // C9 部分更新：显式置 null 的环境列必须走 wrapper 更新，而非 updateById
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
    void executeNowSceneReturnsRunningWithExecutionId() {
        ApiScheduledTask task = existingTask();
        task.setLastExecutionStatus("success");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        String executionId = UUID.randomUUID().toString();
        when(taskRunner.launchSceneAsyncFinalize(task, USER_ID, "manual"))
                .thenReturn(new ScheduledTaskRunner.SceneLaunch(WORKSPACE_ID, executionId));

        ApiScheduleExecuteNowRespDTO resp =
                service.executeNow(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID);

        assertEquals(executionId, resp.getExecutionId().toString());
        assertEquals("running", resp.getStatus());
    }

    @Test
    void executeNowImportReturnsFinalOutcome() {
        ApiScheduledTask task = existingTask();
        task.setTaskType("import_swagger");
        task.setLastExecutionStatus(null);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task);
        UUID importId = UUID.randomUUID();
        when(taskRunner.runImportRethrow(task, USER_ID, "manual"))
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
        when(importRecordMapper.selectBatchIds(List.of(importId))).thenReturn(List.of(importRecord));

        PageResult<ApiScheduleExecutionItemRespDTO> page =
                service.executions(WORKSPACE_ID, PROJECT_ID, USER_ID, TASK_ID, new PageParam());

        assertEquals(1, page.getList().size());
        assertEquals(Map.of("created", 3), page.getList().get(0).getImportSummary());
        assertEquals("scheduled", page.getList().get(0).getTriggerType());
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

    // ========== 系统身份直通（差异点④变更：调度不做成员校验，见 ProjectAccessGuardTest） ==========

    // ========== 夹具 ==========

    private ApiScheduleSaveReqDTO baseReq(String taskType, String cron) {
        ApiScheduleSaveReqDTO reqDTO = new ApiScheduleSaveReqDTO();
        reqDTO.setTaskType(taskType);
        reqDTO.setName("夜间回归");
        reqDTO.setBoundObjectId(UUID.randomUUID());
        reqDTO.setEnvironmentId(UUID.randomUUID());
        reqDTO.setCronExpression(cron);
        return reqDTO;
    }

    private void stubValidEnvironment(ApiScheduleSaveReqDTO reqDTO) {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(reqDTO.getEnvironmentId());
        env.setProjectId(PROJECT_ID);
        when(environmentMapper.selectById(reqDTO.getEnvironmentId())).thenReturn(env);
    }

    private ApiScene stubValidScene(UUID sceneId, int enabledSteps) {
        ApiScene scene = new ApiScene();
        scene.setId(sceneId);
        scene.setProjectId(PROJECT_ID);
        scene.setName("登录链路回归");
        java.util.ArrayList<java.util.Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < enabledSteps; i++) {
            java.util.Map<String, Object> step = new java.util.LinkedHashMap<>();
            step.put("id", UUID.randomUUID());
            step.put("enabled", true);
            steps.add(step);
        }
        scene.setSteps(steps);
        when(sceneMapper.selectById(sceneId)).thenReturn(scene);
        return scene;
    }

    private ApiScheduledTask existingTask() {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setTaskType("scene_execute");
        task.setName("夜间回归");
        task.setBoundObjectId(UUID.randomUUID());
        task.setEnvironmentId(UUID.randomUUID());
        task.setCronExpression("*/5 * * * *");
        task.setEnabled(true);
        return task;
    }
}
