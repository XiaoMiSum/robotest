package io.github.xiaomisum.robotest.service.apitest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import jakarta.annotation.Resource;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_ENV_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_CRON_INVALID;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_ENV_REQUIRED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_MODULE_IDS_REQUIRED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_MODULE_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_OPENAPI_URL_REQUIRED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_RUNNING;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_SCENE_IDS_REQUIRED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_SCENE_NOT_EXECUTABLE;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_SCOPE_INVALID;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCENE_NOT_FOUND;

/**
 * 定时任务管理实现（定时任务详细设计 3.1）
 */
@Service
public class ApiScheduleServiceImpl implements ApiScheduleService {
private static final String TYPE_TEST_PLAN = "scene_execute";

    private static final String TYPE_SYNC = "import_swagger";
    private static final String SCOPE_ALL = "all";
    private static final String SCOPE_MODULES = "modules";
    private static final String SCOPE_SCENES = "scenes";
    private static final Set<String> SCOPES = Set.of(SCOPE_ALL, SCOPE_MODULES, SCOPE_SCENES);

    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ImportSourceFetcher importSourceFetcher;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ProjectModuleMapper moduleMapper;
    @Resource
    private ApiImportRecordMapper importRecordMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ScheduledTaskRunner taskRunner;
    @Resource
    private ApiTestTaskScheduler apiTestTaskScheduler;

    @Override
    public PageResult<ApiSchedulePageItemRespDTO> page(UUID workspaceId, UUID projectId, UUID userId,
            String taskType, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        PageResult<ApiScheduledTask> pageResult = taskMapper.selectPage(pageParam,
                new LambdaQueryWrapperX<ApiScheduledTask>()
                        .eq(ApiScheduledTask::getProjectId, projectId)
                        .eqIfPresent(ApiScheduledTask::getTaskType, taskType)
                        .orderByDesc(ApiScheduledTask::getCreatedAt));
        List<UUID> envIds = pageResult.getList().stream()
                .map(ApiScheduledTask::getEnvironmentId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, String> envNames = envIds.isEmpty() ? Map.of()
                : environmentMapper.selectBatchIds(envIds).stream()
                        .collect(Collectors.toMap(ApiEnvironment::getId, ApiEnvironment::getName));
        LocalDateTime now = LocalDateTime.now();
        List<ApiSchedulePageItemRespDTO> items = pageResult.getList().stream()
                .map(task -> toPageItem(task, envNames.get(task.getEnvironmentId()), now))
                .toList();
        return new PageResult<>(items, pageResult.getTotal());
    }

    private ApiSchedulePageItemRespDTO toPageItem(ApiScheduledTask task, String environmentName, LocalDateTime now) {
        return ApiSchedulePageItemRespDTO.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .name(task.getName())
                .description(task.getDescription())
                .boundObjectId(task.getBoundObjectId())
                .boundObjectName(task.getBoundObjectName())
                .executionScope(task.getExecutionScope())
                .moduleIds(task.getModuleIds())
                .sceneIds(task.getSceneIds())
                .openapiUrl(task.getOpenapiUrl())
                .environmentId(task.getEnvironmentId())
                .environmentName(environmentName)
                .cronExpression(task.getCronExpression())
                .enabled(task.getEnabled())
                .lastExecutionStatus(task.getLastExecutionStatus())
                .lastExecutionAt(task.getLastExecutionAt())
                // 启用任务才预览下次执行时间；停用任务前端展示 "-"
                // why: cron 触发时刻为服务器本地钟面，接口统一回 UTC 钟面（前端按浏览器时区还原，避免 +8 偏移）
                .nextExecutions(Boolean.TRUE.equals(task.getEnabled())
                        ? CronSupport.nextN(task.getCronExpression(), now, 3)
                                .stream().map(ApiScheduleServiceImpl::toUtcWallClock).toList()
                        : List.of())
                .createdAt(task.getCreatedAt())
                .build();
    }

    @Override
    public ApiScheduleCreatedRespDTO create(UUID workspaceId, UUID projectId, UUID userId,
            ApiScheduleSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        validateCronOrThrow(reqDTO.getCronExpression());
        validateBinding(projectId, reqDTO);

        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(UUID.randomUUID());
        task.setProjectId(projectId);
        task.setTaskType(reqDTO.getTaskType());
        task.setName(reqDTO.getName().trim());
        task.setDescription(reqDTO.getDescription());
        task.setExecutionScope(TYPE_TEST_PLAN.equals(reqDTO.getTaskType()) ? reqDTO.getExecutionScope() : null);
        task.setModuleIds(TYPE_TEST_PLAN.equals(reqDTO.getTaskType())
                && SCOPE_MODULES.equals(reqDTO.getExecutionScope()) ? reqDTO.getModuleIds() : null);
        task.setSceneIds(TYPE_TEST_PLAN.equals(reqDTO.getTaskType())
                && SCOPE_SCENES.equals(reqDTO.getExecutionScope()) ? reqDTO.getSceneIds() : null);
        task.setOpenapiUrl(TYPE_SYNC.equals(reqDTO.getTaskType()) ? reqDTO.getOpenapiUrl().trim() : null);
        task.setEnvironmentId(TYPE_TEST_PLAN.equals(reqDTO.getTaskType()) ? reqDTO.getEnvironmentId() : null);
        task.setCronExpression(reqDTO.getCronExpression().trim());
        task.setEnabled(reqDTO.getEnabled() != null ? reqDTO.getEnabled() : Boolean.TRUE);
        task.setCreatedBy(userId);
        taskMapper.insert(task);

        CronExpression expression = CronSupport.parse(task.getCronExpression());
        LocalDateTime nextExecutionAt = expression != null
                ? toUtcWallClock(expression.next(LocalDateTime.now())) : null;
        apiTestTaskScheduler.onTaskChanged(task.getId());
        return ApiScheduleCreatedRespDTO.builder()
                .id(task.getId())
                .nextExecutionAt(nextExecutionAt)
                .build();
    }

    @Override
    public void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiScheduleSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, id);
        validateCronOrThrow(reqDTO.getCronExpression());
        validateBinding(projectId, reqDTO);

        boolean sync = TYPE_SYNC.equals(reqDTO.getTaskType());
        boolean testPlan = TYPE_TEST_PLAN.equals(reqDTO.getTaskType());
        // 显式列更新：类型切换时用不到的字段必须清空，updateById 会静默忽略 null（C9）
        taskMapper.update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getTaskType, reqDTO.getTaskType())
                .set(ApiScheduledTask::getName, reqDTO.getName().trim())
                .set(ApiScheduledTask::getDescription, reqDTO.getDescription())
                .set(ApiScheduledTask::getExecutionScope, testPlan ? reqDTO.getExecutionScope() : null)
                .set(ApiScheduledTask::getModuleIds, testPlan && SCOPE_MODULES.equals(reqDTO.getExecutionScope())
                        ? reqDTO.getModuleIds() : null)
                .set(ApiScheduledTask::getSceneIds, testPlan && SCOPE_SCENES.equals(reqDTO.getExecutionScope())
                        ? reqDTO.getSceneIds() : null)
                .set(ApiScheduledTask::getOpenapiUrl, sync ? reqDTO.getOpenapiUrl().trim() : null)
                .set(ApiScheduledTask::getEnvironmentId, testPlan ? reqDTO.getEnvironmentId() : null)
                .set(ApiScheduledTask::getCronExpression, reqDTO.getCronExpression().trim()));
        // 启停状态由独立端点维护，编辑不隐式改变调度状态
        apiTestTaskScheduler.onTaskChanged(id);
    }

    @Override
    public void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiScheduleToggleReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, id);
        taskMapper.update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getEnabled, reqDTO.getEnabled()));
        apiTestTaskScheduler.onTaskChanged(id);
    }

    @Override
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, id);
        taskMapper.deleteById(id);
        apiTestTaskScheduler.onTaskChanged(id);
    }

    @Override
    public ApiScheduleExecuteNowRespDTO executeNow(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScheduledTask task = requireTask(projectId, id);
        if ("running".equals(task.getLastExecutionStatus())) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_RUNNING);
        }
        if (TYPE_TEST_PLAN.equals(task.getTaskType())) {
            // 快速入队批量场景执行；响应 executionId 回填 taskId 作本次触发句柄（设计 3.1.6）
            taskRunner.launchTestPlanAsyncFinalize(task, userId, "manual");
            return ApiScheduleExecuteNowRespDTO.builder()
                    .executionId(task.getId())
                    .status("running")
                    .build();
        }
        ScheduledTaskRunner.ImportOutcome outcome = taskRunner.runSyncRethrow(task, userId, "manual");
        return ApiScheduleExecuteNowRespDTO.builder()
                .executionId(outcome.importRecordId())
                .status(outcome.status())
                .build();
    }

    @Override
    public PageResult<ApiScheduleExecutionItemRespDTO> executions(UUID workspaceId, UUID projectId, UUID userId,
            UUID taskId, PageParam pageParam) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, taskId);
        PageResult<ApiScheduledTaskExecution> pageResult =
                executionMapper.selectPageByTask(taskId, pageParam);
        List<UUID> importIds = pageResult.getList().stream()
                .map(ApiScheduledTaskExecution::getImportRecordId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, Map<String, Object>> importSummaries = importIds.isEmpty() ? Map.of()
                : importRecordMapper.selectByIds(importIds).stream()
                        .collect(Collectors.toMap(ApiImportRecord::getId, ApiImportRecord::getSummary));
        List<ApiScheduleExecutionItemRespDTO> items = pageResult.getList().stream().map(record -> {
            ApiScheduleExecutionItemRespDTO item = new ApiScheduleExecutionItemRespDTO();
            item.setId(record.getId());
            item.setTriggerType(record.getTriggerType());
            item.setStatus(record.getStatus());
            item.setErrorMessage(record.getErrorMessage());
            item.setReportId(record.getReportId());
            item.setImportRecordId(record.getImportRecordId());
            // 测试计划类型（scene_execute）执行记录无 importRecordId；importSummaries 为不可变空 Map，
            // MapN.get(null) 会抛 NPE，必须显式判空
            item.setImportSummary(record.getImportRecordId() == null
                    ? null
                    : importSummaries.get(record.getImportRecordId()));
            item.setTriggeredAt(record.getTriggeredAt());
            item.setDurationMs(record.getDurationMs());
            return item;
        }).toList();
        return new PageResult<>(items, pageResult.getTotal());
    }

    @Override
    public ApiScheduleValidateCronRespDTO validateCron(ApiScheduleValidateCronReqDTO reqDTO) {
        CronExpression expression = CronSupport.parse(reqDTO.getCronExpression());
        if (expression == null) {
            return ApiScheduleValidateCronRespDTO.builder().valid(false).build();
        }
return ApiScheduleValidateCronRespDTO.builder()
        .valid(true)
        .description(CronSupport.describe(reqDTO.getCronExpression()))
        .nextExecutions(CronSupport.nextN(reqDTO.getCronExpression(), LocalDateTime.now(), 5)
                .stream().map(ApiScheduleServiceImpl::toUtcWallClock).toList())
        .build();
    }

    /**
     * cron 计算的触发时间为服务器本地钟面（调度器按服务器时钟触发）；
     * 对外统一换算为 UTC 钟面发送，前端按浏览器时区还原，避免展示比实际触发时间偏移（UTC+8 环境即 +8）。
     */
    private static LocalDateTime toUtcWallClock(LocalDateTime serverLocal) {
        return serverLocal.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private ApiScheduledTask requireTask(UUID projectId, UUID id) {
        ApiScheduledTask task = taskMapper.selectById(id);
        if (task == null || !task.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_NOT_FOUND);
        }
        return task;
    }

    private void validateCronOrThrow(String cronExpression) {
        if (CronSupport.parse(cronExpression) == null) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_CRON_INVALID);
        }
    }

    /** 任务配置校验（定时任务详细设计 3.1.2 校验规则） */
    private void validateBinding(UUID projectId, ApiScheduleSaveReqDTO reqDTO) {
        if (TYPE_TEST_PLAN.equals(reqDTO.getTaskType())) {
            validateTestPlanBinding(projectId, reqDTO);
            return;
        }
        validateSyncBinding(reqDTO);
    }

    private void validateTestPlanBinding(UUID projectId, ApiScheduleSaveReqDTO reqDTO) {
        if (reqDTO.getEnvironmentId() == null) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_ENV_REQUIRED);
        }
        ApiEnvironment environment = environmentMapper.selectById(reqDTO.getEnvironmentId());
        if (environment == null || !environment.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(API_ENV_NOT_FOUND);
        }
        String scope = reqDTO.getExecutionScope();
        if (!SCOPES.contains(scope)) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_SCOPE_INVALID);
        }
        if (SCOPE_MODULES.equals(scope)) {
            if (reqDTO.getModuleIds() == null || reqDTO.getModuleIds().isEmpty()) {
                throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_MODULE_IDS_REQUIRED);
            }
            requireModulesInProject(projectId, reqDTO.getModuleIds());
        }
        if (SCOPE_SCENES.equals(scope)) {
            if (reqDTO.getSceneIds() == null || reqDTO.getSceneIds().isEmpty()) {
                throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_SCENE_IDS_REQUIRED);
            }
            requireScenesExecutable(projectId, reqDTO.getSceneIds());
        }
    }

    private void requireModulesInProject(UUID projectId, List<UUID> moduleIds) {
        List<ProjectModule> modules = moduleMapper.listByIds(moduleIds);
        if (modules.size() != moduleIds.size()
                || modules.stream().anyMatch(m -> !m.getProjectId().equals(projectId))) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_MODULE_NOT_FOUND);
        }
    }

    private void requireScenesExecutable(UUID projectId, List<UUID> sceneIds) {
        List<ApiScene> scenes = sceneMapper.selectByIds(sceneIds);
        if (scenes.size() != sceneIds.size()
                || scenes.stream().anyMatch(scene -> !scene.getProjectId().equals(projectId))) {
            throw ServiceExceptionUtil.get(API_SCENE_NOT_FOUND);
        }
        // 可执行场景判定口径：至少一个启用步骤（定时任务详细设计 4.3）
        for (ApiScene scene : scenes) {
            long enabledSteps = scene.getSteps() == null ? 0
                    : scene.getSteps().stream()
                            .filter(s -> Boolean.TRUE.equals(s.get("enabled")))
                            .count();
            if (enabledSteps == 0) {
                throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_SCENE_NOT_EXECUTABLE);
            }
        }
    }

    private void validateSyncBinding(ApiScheduleSaveReqDTO reqDTO) {
        if (reqDTO.getOpenapiUrl() == null || reqDTO.getOpenapiUrl().isBlank()) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_OPENAPI_URL_REQUIRED);
        }
        // 保存前校验 URL 可达且通过 SSRF 防护（复用导入链路同一防护口径）
        importSourceFetcher.fetch(reqDTO.getOpenapiUrl().trim());
    }

}