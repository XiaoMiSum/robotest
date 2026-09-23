package io.github.xiaomisum.robotest.controller.admin;

import io.github.xiaomisum.robotest.model.dto.response.admin.DashboardStatsRespDTO;
import io.github.xiaomisum.robotest.service.admin.dashboard.DashboardStatsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    @Resource
    private DashboardStatsService dashboardStatsService;

    /** 与用户/空间列表一致不设独立权限点：进入 /admin 即可读（路由守卫 requiresAdmin 把关） */
    @GetMapping("/stats")
    public Result<DashboardStatsRespDTO> getStats() {
        return Result.ok(dashboardStatsService.getStats());
    }
}
