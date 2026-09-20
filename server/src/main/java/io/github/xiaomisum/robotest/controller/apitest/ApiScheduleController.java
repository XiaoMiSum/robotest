package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiScheduleValidateCronReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleCreatedRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecuteNowRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleExecutionItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSchedulePageItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiScheduleValidateCronRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiScheduleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

/** 定时任务路由（定时任务详细设计 3.1） */
@RestController
public class ApiScheduleController {

    @Resource
    private ApiScheduleService scheduleService;

    @GetMapping("/api/project/scheduled-tasks")
    @PreAuthorize("hasAuthority('api-timer:view')")
    public Result<PageResult<ApiSchedulePageItemRespDTO>> page(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid PageParam pageParam,
            @RequestParam(value = "taskType", required = false) String taskType) {
        return Result.ok(scheduleService.page(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), taskType, pageParam));
    }

    @PostMapping("/api/project/scheduled-tasks")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<ApiScheduleCreatedRespDTO> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid ApiScheduleSaveReqDTO reqDTO) {
        return Result.ok(scheduleService.create(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), reqDTO));
    }

    @PutMapping("/api/project/scheduled-tasks/{id}")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<Boolean> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiScheduleSaveReqDTO reqDTO) {
        scheduleService.update(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @PutMapping("/api/project/scheduled-tasks/{id}/toggle")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<Boolean> toggle(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @RequestBody @Valid ApiScheduleToggleReqDTO reqDTO) {
        scheduleService.toggle(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, reqDTO);
        return Result.ok(true);
    }

    @DeleteMapping("/api/project/scheduled-tasks/{id}")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<Boolean> delete(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        scheduleService.delete(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id);
        return Result.ok(true);
    }

    @PostMapping("/api/project/scheduled-tasks/{id}/execute")
    @PreAuthorize("hasAuthority('api-timer:edit')")
    public Result<ApiScheduleExecuteNowRespDTO> executeNow(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        return Result.ok(scheduleService.executeNow(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id));
    }

    @GetMapping("/api/project/scheduled-tasks/{id}/executions")
    @PreAuthorize("hasAuthority('api-timer:view')")
    public Result<PageResult<ApiScheduleExecutionItemRespDTO>> executions(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id,
            @Valid PageParam pageParam) {
        return Result.ok(scheduleService.executions(loginUser.getActiveWorkspaceId(), loginUser.getActiveProjectId(), loginUser.getId(), id, pageParam));
    }

    @PostMapping("/api/project/scheduled-tasks/validate-cron")
    @PreAuthorize("hasAuthority('api-timer:view')")
    public Result<ApiScheduleValidateCronRespDTO> validateCron(@RequestBody @Valid ApiScheduleValidateCronReqDTO reqDTO) {
        return Result.ok(scheduleService.validateCron(reqDTO));
    }
}
