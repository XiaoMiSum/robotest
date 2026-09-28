package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysPermissionMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.security.core.AuthUserDetails;
import xyz.migoo.framework.security.core.authentication.JwtTokenProvider;
import xyz.migoo.framework.security.core.authentication.UserDetailsBridge;
import xyz.migoo.framework.web.core.store.StateStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 用户加载桥接 + Token 撤销（安全规范 2.3）。
 *
 * <p>登出走单 Token 黑名单，踢人（禁用/锁定/改密）走用户级签发截止；
 * 两者均经框架 {@link StateStore} 存储，检查在每次请求的 Token 校验阶段生效。</p>
 */
@Slf4j
@Component
public class UserDetailsBridgeImpl implements UserDetailsBridge {

    /** 单 Token 黑名单键；值为 1，TTL 7 天（不短于 Refresh Token 上限有效期） */
    private static final String BLACKLIST_KEY_PREFIX = "security:token:blacklist:";
    private static final Duration BLACKLIST_TTL = Duration.ofDays(7);

    /**
     * 用户级签发截止键；值为撤销时刻（epoch 秒），签发早于该时刻的 Token 一律拒绝。
     * TTL 取 30 天：远长于 Token 最长有效期（Refresh 7 天），键自然过期即可清理。
     */
    private static final String REVOKED_BEFORE_KEY_PREFIX = "security:user:revoked-before:";
    private static final Duration REVOKED_BEFORE_TTL = Duration.ofDays(30);

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private SysUserRoleMapper userRoleMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private SysPermissionMapper permissionMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private StateStore stateStore;
    @Resource
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public AuthUserDetails<?, ?> loadByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapperX<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .or()
                        .eq(SysUser::getEmail, username)
        );
        return user != null ? toLoginUser(user) : null;
    }

    @Override
    public AuthUserDetails<?, ?> loadByUserId(String userId) {
        SysUser user = userMapper.selectById(UUID.fromString(userId));
        return user != null ? toLoginUser(user) : null;
    }

    private LoginUser toLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setName(user.getUsername());
        loginUser.setEmail(user.getEmail());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setEnabled(Constants.Status.ACTIVE.equals(user.getStatus()));
        List<SysRole> roles = loadRoles(user.getId());
        loginUser.setAuthorities(buildAuthorities(roles));
        boolean hasWorkspace = workspaceUserMapper.selectCount(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, user.getId())) > 0;
        loginUser.setHasWorkspace(hasWorkspace);
        return loginUser;
    }

    private List<SysRole> loadRoles(UUID userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapperX<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<UUID> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectList(
                new LambdaQueryWrapperX<SysRole>().in(SysRole::getId, roleIds));
    }

    private List<? extends GrantedAuthority> buildAuthorities(List<SysRole> roles) {
        return roles.stream()
                .flatMap(role -> {
                    Stream<GrantedAuthority> roleAuth = Stream.of(
                            new SimpleGrantedAuthority(Constants.Auth.ROLE_PREFIX + role.getName()));
                    Stream<GrantedAuthority> permAuth = role.getPermissions() != null
                            ? role.getPermissions().stream().map(SimpleGrantedAuthority::new)
                            : Stream.empty();
                    return Stream.concat(roleAuth, permAuth);
                })
                .distinct()
                .toList();
    }

    // ========== Token 撤销（安全规范 2.3） ==========

    /** 登出：单 Token 加入黑名单，后续校验（含同 Token 的 refresh）一律 401 */
    @Override
    public void clean(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        stateStore.put(BLACKLIST_KEY_PREFIX + token, 1L, BLACKLIST_TTL);
    }

    /** 踢人：记录签发截止，撤销该时刻前签发的全部 Token，重新登录签发的新 Token 不受影响 */
    @Override
    public void revokeByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        stateStore.put(REVOKED_BEFORE_KEY_PREFIX + userId,
                Instant.now().getEpochSecond(), REVOKED_BEFORE_TTL);
    }

    @Override
    public boolean isTokenRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (isBlacklisted(BLACKLIST_KEY_PREFIX + token)) {
            return true;
        }
        Jwt jwt;
        try {
            jwt = jwtTokenProvider.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 无效 Token 不在此定性，交由框架签名/过期校验返回 401/400
            return false;
        } catch (Exception e) {
            log.warn("[TokenRevocation] 解析 Token 失败，按失败开放处理: {}", e.getMessage());
            return false;
        }
        if (jwt == null) {
            return false;
        }
        Instant issuedAt = jwt.getIssuedAt();
        String userId = tokenProviderUserId(jwt);
        if (issuedAt == null || userId == null) {
            return false;
        }
        return issuedBefore(userId, issuedAt);
    }

    /**
     * 用户级踢出按签发时间生效（见 {@link #isTokenRevoked(String)}）；
     * 此处恒为 false，否则会把撤销后重新登录的新 Token 一并封死。
     */
    @Override
    public boolean isUserRevoked(String userId) {
        return false;
    }

    /** 黑名单命中返回 true；存储故障按失败开放返回 false（不拦截请求） */
    private boolean isBlacklisted(String key) {
        try {
            return stateStore.get(key) > 0;
        } catch (Exception e) {
            log.warn("[TokenRevocation] 黑名单查询失败，按失败开放处理: {}", e.getMessage());
            return false;
        }
    }

    private boolean issuedBefore(String userId, Instant issuedAt) {
        try {
            long revokedBefore = stateStore.get(REVOKED_BEFORE_KEY_PREFIX + userId);
            return revokedBefore > 0 && issuedAt.getEpochSecond() < revokedBefore;
        } catch (Exception e) {
            log.warn("[TokenRevocation] 签发截止查询失败，按失败开放处理: {}", e.getMessage());
            return false;
        }
    }

    private String tokenProviderUserId(Jwt jwt) {
        try {
            return jwtTokenProvider.getUserIdFromToken(jwt);
        } catch (Exception e) {
            log.warn("[TokenRevocation] 读取 userId 失败，按失败开放处理: {}", e.getMessage());
            return null;
        }
    }
}
