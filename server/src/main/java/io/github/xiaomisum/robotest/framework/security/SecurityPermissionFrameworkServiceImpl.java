package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.security.core.service.SecurityPermissionFrameworkService;
import xyz.migoo.framework.web.core.util.WebFrameworkUtils;

import java.util.List;

@Component
public class SecurityPermissionFrameworkServiceImpl implements SecurityPermissionFrameworkService {

    @Resource
    private RoleService roleService;

    @Override
    public boolean hasPermission(String permission) {
        Object userId = WebFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return false;
        }
        List<String> permissions = roleService.getUserPermissionCodes(userId.toString());
        return permissions.contains(permission);
    }

    @Override
    public boolean hasAnyPermissions(String... permissions) {
        Object userId = WebFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return false;
        }
        List<String> userPermissions = roleService.getUserPermissionCodes(userId.toString());
        for (String perm : permissions) {
            if (userPermissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasRole(String role) {
        return false;
    }

    @Override
    public boolean hasAnyRoles(String... roles) {
        return false;
    }
}
