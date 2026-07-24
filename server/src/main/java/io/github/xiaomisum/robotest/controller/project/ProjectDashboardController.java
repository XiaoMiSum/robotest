package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/project/dashboard")
public class ProjectDashboardController {

    @Resource
    private ProjectDashboardService projectDashboardService;

    @GetMapping
    public Result<ProjectDashboardRespDTO> getDashboard(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId) {
        return Result.ok(projectDashboardService.getDashboard(projectId));
    }
}
