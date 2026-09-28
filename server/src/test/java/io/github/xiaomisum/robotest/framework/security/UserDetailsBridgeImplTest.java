package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import xyz.migoo.framework.security.core.authentication.JwtTokenProvider;
import xyz.migoo.framework.web.core.store.StateStore;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsBridgeImplTest {

    private static final String TOKEN = "jwt-token";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000002";
    private static final String BLACKLIST_KEY = "security:token:blacklist:" + TOKEN;
    private static final String REVOKED_BEFORE_KEY = "security:user:revoked-before:" + USER_ID;

    @Mock
    private StateStore stateStore;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserDetailsBridgeImpl bridge;

    private Jwt jwt(Instant issuedAt) {
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "HS256")
                .claim("userId", USER_ID)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .build();
    }

    // ========== clean（登出单 Token 黑名单）==========

    @Test
    void clean_writesBlacklistKey() {
        bridge.clean(TOKEN);

        verify(stateStore).put(eq(BLACKLIST_KEY), eq(1L), eq(Duration.ofDays(7)));
    }

    @Test
    void clean_blankToken_isIgnored() {
        bridge.clean(" ");
        bridge.clean(null);

        verify(stateStore, never()).put(anyString(), anyLong(), any(Duration.class));
    }

    // ========== revokeByUserId（踢人签发截止）==========

    @Test
    void revokeByUserId_writesIssuedBeforeCutoff() {
        bridge.revokeByUserId(USER_ID);

        // 值为撤销时刻（epoch 秒），键在 30 天后自然过期清理
        verify(stateStore).put(eq(REVOKED_BEFORE_KEY), longThat(v -> v > 0), eq(Duration.ofDays(30)));
    }

    @Test
    void revokeByUserId_blankUserId_isIgnored() {
        bridge.revokeByUserId(null);

        verify(stateStore, never()).put(anyString(), anyLong(), any(Duration.class));
    }

    // ========== isTokenRevoked ==========

    @Test
    void isTokenRevoked_blacklisted_returnsTrue() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(1L);

        assertTrue(bridge.isTokenRevoked(TOKEN));
        // 黑名单命中即定性，无需再解析 Token
        verify(jwtTokenProvider, never()).parseToken(anyString());
    }

    @Test
    void isTokenRevoked_issuedBeforeCutoff_returnsTrue() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(0L);
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(jwt(Instant.now().minusSeconds(60)));
        when(jwtTokenProvider.getUserIdFromToken(any(Jwt.class))).thenReturn(USER_ID);
        when(stateStore.get(REVOKED_BEFORE_KEY)).thenReturn(Instant.now().getEpochSecond());

        assertTrue(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_issuedAfterCutoff_returnsFalse() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(0L);
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(jwt(Instant.now()));
        when(jwtTokenProvider.getUserIdFromToken(any(Jwt.class))).thenReturn(USER_ID);
        when(stateStore.get(REVOKED_BEFORE_KEY)).thenReturn(Instant.now().minusSeconds(60).getEpochSecond());

        assertFalse(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_noCutoffKey_returnsFalse() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(0L);
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(jwt(Instant.now().minusSeconds(60)));
        when(jwtTokenProvider.getUserIdFromToken(any(Jwt.class))).thenReturn(USER_ID);
        when(stateStore.get(REVOKED_BEFORE_KEY)).thenReturn(0L);

        assertFalse(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_invalidToken_returnsFalse() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(0L);
        when(jwtTokenProvider.parseToken(TOKEN)).thenThrow(new JwtException("malformed"));

        // 无效 Token 不在此定性，交由框架签名/过期校验返回 401/400
        assertFalse(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_storeFailure_failsOpen() {
        when(stateStore.get(BLACKLIST_KEY)).thenThrow(new IllegalStateException("redis down"));
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(jwt(Instant.now().minusSeconds(60)));
        when(jwtTokenProvider.getUserIdFromToken(any(Jwt.class))).thenReturn(USER_ID);

        // 黑名单读取故障不拦截请求（失败开放）
        assertFalse(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_parseReturnsNull_returnsFalse() {
        when(stateStore.get(BLACKLIST_KEY)).thenReturn(0L);
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(null);

        assertFalse(bridge.isTokenRevoked(TOKEN));
    }

    @Test
    void isTokenRevoked_blankToken_returnsFalse() {
        assertFalse(bridge.isTokenRevoked(""));
        assertFalse(bridge.isTokenRevoked(null));

        verify(stateStore, never()).get(anyString());
    }

    // ========== isUserRevoked ==========

    @Test
    void isUserRevoked_alwaysFalse() {
        // 踢人按签发时间在 isTokenRevoked 中生效，此处不能按 userId 一刀切，
        // 否则撤销后重新登录的新 Token 也会被封死
        assertFalse(bridge.isUserRevoked(USER_ID));
    }
}
