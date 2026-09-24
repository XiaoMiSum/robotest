package io.github.xiaomisum.robotest.controller.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.service.admin.RoleService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.migoo.framework.common.pojo.Result;

import java.util.List;

/**
 * 空间侧角色选项：成员列表的角色标签/筛选/改角色下拉使用，
 * 避免工作空间页面依赖 /api/admin/roles（SEC-002：工作空间角色不得访问管理端 API）
 */
@RestController
@RequestMapping("/api/workspace/roles")
public class WorkspaceRoleController {

    @Resource
    private RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('ws-member:view')")
    public Result<List<RoleSimpleRespDTO>> getRoleOptions() {
        return Result.ok(roleService.getRoleList(Constants.RoleType.WORKSPACE));
    }
}
