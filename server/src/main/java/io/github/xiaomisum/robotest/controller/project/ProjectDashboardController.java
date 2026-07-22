package io.github.xiaomisum.robotest.controller.project;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ProjectDashboardRespDTO;
import io.github.xiaomisum.robotest.service.project.ProjectDashboardService;
import jakarta.annotation.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project/dashboard")
public class ProjectDashboardController {

    @Resource
    private ProjectDashboardService projectDashboardService;

    @GetMapping
    public ProjectDashboardRespDTO getDashboard(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestHeader("X-Active-Project") String projectId) {
        return projectDashboardService.getDashboard(projectId);
    }
}
