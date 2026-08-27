package io.github.xiaomisum.robotest.service.apitest;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
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
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStep;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiImportRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import jakarta.annotation.Resource;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_ENV_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_CRON_INVALID;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_ENV_REQUIRED;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_RUNNING;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCHEDULED_TASK_SCENE_NOT_EXECUTABLE;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SCENE_NOT_FOUND;
import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_SWAGGER_URL_NOT_FOUND;

/**
 * 定时任务管理实现（定时任务详细设计 3.1）
 */
@Service
public class ApiScheduleServiceImpl implements ApiScheduleService {

    private static final String TYPE_SCENE = "scene_execute";
    private static final String TYPE_IMPORT = "import_swagger";

    @Resource
    private ApiScheduledTaskMapper taskMapper;
    @Resource
    private ApiScheduledTaskExecutionMapper executionMapper;
    @Resource
    private ApiSwaggerUrlMapper swaggerUrlMapper;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ApiSceneStepMapper stepMapper;
    @Resource
    private ApiEnvironmentMapper environmentMapper;
    @Resource
    private ApiImportRecordMapper importRecordMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ScheduledTaskRunner taskRunner;
    @Resource
    private ApiTestTaskScheduler taskScheduler;

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
                .environmentId(task.getEnvironmentId())
                .environmentName(environmentName)
                .cronExpression(task.getCronExpression())
                .enabled(task.getEnabled())
                .lastExecutionStatus(task.getLastExecutionStatus())
                .lastExecutionAt(task.getLastExecutionAt())
                // 启用任务才预览下次执行时间；停用任务前端展示 "-"
                .nextExecutions(Boolean.TRUE.equals(task.getEnabled())
                        ? CronSupport.nextN(task.getCronExpression(), now, 3) : List.of())
                .createdAt(task.getCreatedAt())
                .build();
    }

    @Override
    public ApiScheduleCreatedRespDTO create(UUID workspaceId, UUID projectId, UUID userId,
            ApiScheduleSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        validateCronOrThrow(reqDTO.getCronExpression());
        String boundObjectName = validateBinding(projectId, reqDTO);

        ApiScheduledTask task = new ApiScheduledTask();
        task.setId(UUID.randomUUID());
        task.setProjectId(projectId);
        task.setTaskType(reqDTO.getTaskType());
        task.setName(reqDTO.getName().trim());
        task.setDescription(reqDTO.getDescription());
        task.setBoundObjectId(reqDTO.getBoundObjectId());
        task.setBoundObjectName(boundObjectName);
        task.setEnvironmentId(reqDTO.getEnvironmentId());
        task.setCronExpression(reqDTO.getCronExpression().trim());
        task.setEnabled(reqDTO.getEnabled() != null ? reqDTO.getEnabled() : Boolean.TRUE);
        task.setCreatedBy(userId);
        taskMapper.insert(task);

        CronExpression expression = CronSupport.parse(task.getCronExpression());
        LocalDateTime nextExecutionAt = expression != null ? expression.next(LocalDateTime.now()) : null;
        taskScheduler.onTaskChanged(task.getId());
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
        String boundObjectName = validateBinding(projectId, reqDTO);

        // 显式列更新：切到导入类型时环境必须清空，updateById 会静默忽略 null（C9）
        taskMapper.update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getTaskType, reqDTO.getTaskType())
                .set(ApiScheduledTask::getName, reqDTO.getName().trim())
                .set(ApiScheduledTask::getDescription, reqDTO.getDescription())
                .set(ApiScheduledTask::getBoundObjectId, reqDTO.getBoundObjectId())
                .set(ApiScheduledTask::getBoundObjectName, boundObjectName)
                .set(ApiScheduledTask::getEnvironmentId, TYPE_SCENE.equals(reqDTO.getTaskType())
                        ? reqDTO.getEnvironmentId() : null)
                .set(ApiScheduledTask::getCronExpression, reqDTO.getCronExpression().trim()));
        // 启停状态由独立端点维护，编辑不隐式改变调度状态
        taskScheduler.onTaskChanged(id);
    }

    @Override
    public void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiScheduleToggleReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, id);
        taskMapper.update(null, new LambdaUpdateWrapperX<ApiScheduledTask>()
                .eq(ApiScheduledTask::getId, id)
                .set(ApiScheduledTask::getEnabled, reqDTO.getEnabled()));
        taskScheduler.onTaskChanged(id);
    }

    @Override
    public void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        requireTask(projectId, id);
        taskMapper.deleteById(id);
        taskScheduler.onTaskChanged(id);
    }

    @Override
    public ApiScheduleExecuteNowRespDTO executeNow(UUID workspaceId, UUID projectId, UUID userId, UUID id) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        ApiScheduledTask task = requireTask(projectId, id);
        if ("running".equals(task.getLastExecutionStatus())) {
            throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_RUNNING);
        }
        if (TYPE_SCENE.equals(task.getTaskType())) {
            ScheduledTaskRunner.SceneLaunch launch =
                    taskRunner.launchSceneAsyncFinalize(task, userId, "manual");
            return ApiScheduleExecuteNowRespDTO.builder()
                    .executionId(UUID.fromString(launch.sceneExecutionId()))
                    .status("running")
                    .build();
        }
        ScheduledTaskRunner.ImportOutcome outcome = taskRunner.runImportRethrow(task, userId, "manual");
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
                .map(record -> record.getImportRecordId())
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, Map<String, Object>> importSummaries = importIds.isEmpty() ? Map.of()
                : importRecordMapper.selectBatchIds(importIds).stream()
                        .collect(Collectors.toMap(ApiImportRecord::getId, ApiImportRecord::getSummary));
        List<ApiScheduleExecutionItemRespDTO> items = pageResult.getList().stream().map(record -> {
            ApiScheduleExecutionItemRespDTO item = new ApiScheduleExecutionItemRespDTO();
            item.setId(record.getId());
            item.setTriggerType(record.getTriggerType());
            item.setStatus(record.getStatus());
            item.setErrorMessage(record.getErrorMessage());
            item.setReportId(record.getReportId());
            item.setImportRecordId(record.getImportRecordId());
            item.setImportSummary(importSummaries.get(record.getImportRecordId()));
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
                .nextExecutions(CronSupport.nextN(reqDTO.getCronExpression(), LocalDateTime.now(), 5))
                .build();
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

    /** 绑定对象存在性校验并返回名称快照（定时任务详细设计 3.1.2 校验规则） */
    private String validateBinding(UUID projectId, ApiScheduleSaveReqDTO reqDTO) {
        if (TYPE_SCENE.equals(reqDTO.getTaskType())) {
            if (reqDTO.getEnvironmentId() == null) {
                throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_ENV_REQUIRED);
            }
            ApiEnvironment environment = environmentMapper.selectById(reqDTO.getEnvironmentId());
            if (environment == null || !environment.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(API_ENV_NOT_FOUND);
            }
            ApiScene scene = sceneMapper.selectById(reqDTO.getBoundObjectId());
            if (scene == null || !scene.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(API_SCENE_NOT_FOUND);
            }
            // 可执行场景判定口径：至少一个启用步骤（差异点③）
            Long enabledSteps = stepMapper.selectCount(new LambdaQueryWrapperX<ApiSceneStep>()
                    .eq(ApiSceneStep::getSceneId, scene.getId())
                    .eq(ApiSceneStep::getEnabled, true));
            if (enabledSteps == null || enabledSteps == 0) {
                throw ServiceExceptionUtil.get(API_SCHEDULED_TASK_SCENE_NOT_EXECUTABLE);
            }
            return scene.getName();
        }
        ApiSwaggerUrl config = swaggerUrlMapper.selectById(reqDTO.getBoundObjectId());
        if (config == null || !config.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(API_SWAGGER_URL_NOT_FOUND);
        }
        return config.getName();
    }

}
