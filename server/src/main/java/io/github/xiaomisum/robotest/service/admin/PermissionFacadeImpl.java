package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限裁决门面：按 scope 路由到对应 {@link PermissionChecker}。
 *
 * <p>新增策略只做"新增一个 PermissionChecker 实现"，无需改动门面路由逻辑。</p>
 */
@Service
public class PermissionFacadeImpl implements PermissionFacade {

    private final Map<PermissionScope, PermissionChecker> checkers;

    public PermissionFacadeImpl(java.util.List<PermissionChecker> checkerList) {
        this.checkers = checkerList.stream()
                .collect(Collectors.toMap(this::scopeOf, Function.identity()));
    }

    @Override
    public Set<String> permissionsOf(UUID userId, PermissionScope scope, UUID workspaceId) {
        PermissionChecker checker = requireChecker(scope);
        return checker.codes(userId, workspaceId);
    }

    @Override
    public void require(UUID userId, PermissionScope scope, UUID workspaceId, String code) {
        if (!permissionsOf(userId, scope, workspaceId).contains(code)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private PermissionChecker requireChecker(PermissionScope scope) {
        PermissionChecker checker = checkers.get(scope);
        if (checker == null) {
            throw new IllegalStateException("缺少 PermissionChecker 实现: " + scope);
        }
        return checker;
    }

    private PermissionScope scopeOf(PermissionChecker checker) {
        if (checker instanceof SystemPermissionChecker) return PermissionScope.SYSTEM;
        if (checker instanceof WorkspacePermissionChecker) return PermissionScope.WORKSPACE;
        throw new IllegalStateException("无法识别 PermissionChecker 作用域: " + checker.getClass());
    }
}