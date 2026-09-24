package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.response.admin.DashboardStatsRespDTO;
import io.github.xiaomisum.robotest.service.admin.dashboard.DashboardStatsService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    @Resource
    private DashboardStatsService dashboardStatsService;

    // 无独立 dashboard 权限点：与前端 requiresAdmin 对齐，持任一系统管理查看权限即可读，
    // 仅工作空间角色（无系统权限码）在 /api/admin 路径下为空权限集，必然 403
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('user:view', 'workspace:view', 'role:view')")
    public Result<DashboardStatsRespDTO> getStats() {
        return Result.ok(dashboardStatsService.getStats());
    }
}
