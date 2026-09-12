package io.github.xiaomisum.robotest.service.admin;

import java.util.Set;
import java.util.UUID;

/**
 * 按作用域取权限码的读取端口，由 {@link PermissionFacade} 按 scope 路由。
 *
 * <p>SYSTEM 忽略 workspaceId；WORKSPACE 必须携带 workspaceId 才有意义。</p>
 */
public interface PermissionChecker {

    Set<String> codes(UUID userId, UUID workspaceId);
}