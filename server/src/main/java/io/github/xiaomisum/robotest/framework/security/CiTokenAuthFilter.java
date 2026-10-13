package io.github.xiaomisum.robotest.framework.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * CI 侧只读令牌认证（GitLab 集成详细设计 4.6）：校验 ROBOTEST-CI-TOKEN 头与平台签发令牌一致。
 * <p>令牌为部署级静态值（平台配置 + GitLab CI/CD 变量双侧同值），fail-closed：未配置令牌时
 * 一律拒绝；批次票据本身为随机 UUID 不可猜，令牌为纵深防御。仅覆盖 /api/ci/*，不影响用户链路。
 */
public class CiTokenAuthFilter implements Filter {

    public static final String TOKEN_HEADER = "ROBOTEST-CI-TOKEN";

    private final String expectedToken;

    public CiTokenAuthFilter(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        // fail-closed：平台未配置令牌时 CI 端点整体不可用，避免匿名读取批次清单
        if (expectedToken.isEmpty() || !matches(httpRequest.getHeader(TOKEN_HEADER))) {
            writeUnauthorized(httpResponse);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 常量时间比对，避免逐字节短路造成的时间侧信道 */
    private boolean matches(String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse httpResponse) throws IOException {
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.getWriter().write(JsonUtils.toJsonString(java.util.Map.of(
                "code", 401, "msg", "CI 令牌无效或未配置", "data", java.util.Map.of())));
        httpResponse.getWriter().flush();
    }
}
