package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

import java.util.UUID;

@RestController
@RequestMapping("/api/project/dashboard")
public class ProjectDashboardController {

    @Resource
    private ProjectDashboardService projectDashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('project:view')")
    public Result<ProjectDashboardRespDTO> getDashboard(
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(projectDashboardService.getDashboard(
                loginUser.getActiveProjectId(),
                loginUser.getActiveWorkspaceId(),
                loginUser.getId()));
    }
}
