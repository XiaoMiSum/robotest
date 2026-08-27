package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/ai/tasks")
public class AiTaskController {

    @Resource
    private AiTaskService aiTaskService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<AiTaskRespDTO> getTask(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") UUID projectId,
            @PathVariable UUID id) {
        return Result.ok(aiTaskService.getTask(id, projectId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<Void> cancelTask(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        aiTaskService.cancelTask(id, loginUser.getId());
        return Result.ok();
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('case:view')")
    public Result<Void> retryTask(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable UUID id) {
        aiTaskService.retryTask(id, loginUser.getId());
        return Result.ok();
    }
}
