package io.github.xiaomisum.robotest.controller.ai;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.pojo.Result;

import java.util.Map;
import java.util.UUID;

/**
 * 统一任务资源接口（详设 3.6）：仅路由 + 参数校验（C2）。
 *
 * <p>不使用 @PreAuthorize：详设 3.6.2 要求总开关（1000018101）先于权限（1000018116）判定，
 * 框架 403 会短路该顺序，故权限校验在服务层完成。</p>
 */
@RestController
@RequestMapping("/api/ai/tasks")
@Tag(name = "AI 任务", description = "统一任务资源：提交、轮询、产物查看、取消、重试与确认")
public class AiTaskController {

    @Resource
    private AiTaskService aiTaskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AiTaskRespDTO> submit(@AuthenticationPrincipal LoginUser loginUser,
            @RequestBody @Valid AiTaskSubmitReqDTO reqDTO) {
        return Result.ok(aiTaskService.submit(reqDTO, loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(), loginUser.getId(), loginUser));
    }

    @GetMapping
    public Result<PageResult<AiTaskRespDTO>> page(@AuthenticationPrincipal LoginUser loginUser,
            @Valid AiTaskPageReqDTO pageReq) {
        return Result.ok(aiTaskService.page(pageReq, loginUser.getActiveProjectId(), loginUser.getId()));
    }

    @GetMapping("/{taskId}")
    public Result<AiTaskRespDTO> getDetail(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId) {
        return Result.ok(aiTaskService.getDetail(taskId, loginUser));
    }

    @GetMapping("/{taskId}/artifacts/{artifactKey}")
    public Result<Map<String, Object>> getArtifact(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @PathVariable String artifactKey) {
        return Result.ok(aiTaskService.getArtifact(taskId, artifactKey, loginUser));
    }

    @PostMapping("/{taskId}/cancel")
    public Result<AiTaskRespDTO> cancel(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId) {
        return Result.ok(aiTaskService.cancel(taskId, loginUser));
    }

    @PostMapping("/{taskId}/retry")
    public Result<AiTaskRespDTO> retry(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId) {
        return Result.ok(aiTaskService.retry(taskId, loginUser));
    }

    @PostMapping("/{taskId}/artifacts/confirm")
    public Result<AiTaskConfirmRespDTO> confirm(@AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID taskId, @RequestBody @Valid AiTaskConfirmReqDTO reqDTO) {
        return Result.ok(aiTaskService.confirm(taskId, reqDTO, loginUser));
    }
}
