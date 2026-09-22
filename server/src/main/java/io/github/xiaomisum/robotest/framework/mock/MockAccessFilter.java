package io.github.xiaomisum.robotest.framework.mock;

import tools.jackson.databind.JsonNode;
import io.github.xiaomisum.robotest.framework.mock.MockDefinitionReader.MockDefinitionSnapshot;
import io.github.xiaomisum.robotest.service.apitest.mock.MockMatchEngine;
import io.github.xiaomisum.robotest.service.apitest.mock.MockRateLimiter;
import io.github.xiaomisum.robotest.service.apitest.mock.MockResponseFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 免登录 Mock 响应服务（Mock服务详细设计 3.3/4.1/6.1）。
 * 注册于安全过滤器之前，仅当存在命中规则时短路请求；未命中一律放行回平台链路。
 */
public class MockAccessFilter implements Filter {

    private static final int BODY_LOG_LIMIT = 4096;
    private static final int DELAY_UPPER_BOUND_MS = 60_000;

    private final MockDefinitionReader reader;
    private final MockAccessProperties properties;
    private final MockRateLimiter rateLimiter;

    public MockAccessFilter(MockDefinitionReader reader, MockAccessProperties properties) {
        this.reader = reader;
        this.properties = properties;
        this.rateLimiter = new MockRateLimiter(properties.getPathQps());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!properties.isAccessEnabled() || !(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        String path = httpRequest.getRequestURI();
        if (excluded(httpRequest, path)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            handleMock(httpRequest, response, path, chain);
        } catch (Exception e) {
            chain.doFilter(request, response);
        }
    }

    private boolean excluded(HttpServletRequest request, String path) {
        Integer mockPort = properties.getPort();
        if (mockPort != null) {
            return request.getLocalPort() != mockPort;
        }
        return properties.getExcludedPrefixes().stream().anyMatch(path::startsWith);
    }

    private void handleMock(HttpServletRequest request, ServletResponse response, String path, FilterChain chain)
            throws IOException, ServletException {
        String method = request.getMethod();
        List<MockDefinitionSnapshot> candidates = new ArrayList<>(reader.findEnabledForMatch(method, path));
        candidates.addAll(reader.findEnabledWildcards(method));
        if (candidates.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        boolean needBody = candidates.stream()
                .map(MockDefinitionSnapshot::matchRules)
                .anyMatch(rules -> rules != null && rules.stream()
                        .anyMatch(rule -> "body".equals(String.valueOf(rule.get("type")))));
        CachedBodyRequest cachedBodyRequest = null;
        JsonNode bodyNode = null;
        if (needBody && request.getContentLengthLong() > 0) {
            cachedBodyRequest = new CachedBodyRequest(request);
            bodyNode = cachedBodyRequest.parseJson();
        }
        Map<String, String> queryParams = extractQueryParams(request);

        MockDefinitionSnapshot hit = null;
        for (MockDefinitionSnapshot candidate : candidates) {
            if (MockMatchEngine.matches(candidate, method, path,
                    extractHeaders(request), queryParams, bodyNode)) {
                hit = candidate;
                break;
            }
        }
        if (hit == null) {
            chain.doFilter(cachedBodyRequest != null ? cachedBodyRequest : request, response);
            return;
        }

        HttpServletRequest servletRequest = cachedBodyRequest != null ? cachedBodyRequest : request;
        if (!rateLimiter.allow(hit.path())) {
            writeSimple(response, 429, MediaType.TEXT_PLAIN_VALUE, "mock rate limit exceeded");
            logAccessAsync(hit, servletRequest, 429, "rate limit exceeded", 0);
            return;
        }

        long start = System.currentTimeMillis();
        Map<String, Object> example = reader.loadResponseExample(hit.interfaceId());
        MockResponseFactory.MockResponse mockResponse = MockResponseFactory.build(hit, example);
        applyDelay(hit);
        long duration = System.currentTimeMillis() - start;

        writeResponse(response, mockResponse);
        reader.incrementHitAsync(hit.id());
        logAccessAsync(hit, servletRequest, mockResponse.status(),
                truncate(mockResponse.body()), (int) Math.min(duration, Integer.MAX_VALUE));
    }

    private void applyDelay(MockDefinitionSnapshot hit) {
        int delay = hit.delayMs() == null ? 0 : Math.min(hit.delayMs(), DELAY_UPPER_BOUND_MS);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void writeResponse(ServletResponse response, MockResponseFactory.MockResponse mockResponse)
            throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setContentType(mockResponse.headers()
                .getOrDefault("Content-Type", MediaType.TEXT_PLAIN_VALUE));
        mockResponse.headers().forEach((key, value) -> {
            if (!"Content-Type".equalsIgnoreCase(key)) {
                httpResponse.setHeader(key, value);
            }
        });
        httpResponse.setStatus(mockResponse.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = mockResponse.body();
        if (body != null && !body.isEmpty()) {
            response.getWriter().write(body);
        }
        response.getWriter().flush();
    }

    private void writeSimple(ServletResponse response, int status, String contentType, String message)
            throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setContentType(contentType);
        httpResponse.setStatus(status);
        response.getWriter().write(message);
        response.getWriter().flush();
    }

    private void logAccessAsync(MockDefinitionSnapshot hit, HttpServletRequest request, int status,
                                String responseBody, int durationMs) {
        Map<String, String> requestHeaders = extractHeaders(request);
        String requestBody = request instanceof CachedBodyRequest cached ? cached.bodyText() : null;
        String clientIp = resolveClientIp(request);
        reader.logAccessAsync(hit.id(), hit.projectId(), hit.method(), hit.path(),
                requestHeaders, requestBody, status, responseBody, durationMs, clientIp);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > BODY_LOG_LIMIT ? text.substring(0, BODY_LOG_LIMIT) : text;
    }

    private static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    private static Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /** 仅在候选规则包含 body 匹配时缓存请求体；未命中的下游继续从包装件读取完整内容 */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        String bodyText() {
            return body == null ? null : new String(body, StandardCharsets.UTF_8);
        }

        JsonNode parseJson() {
            return JsonUtils.toJSON(bodyText());
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body == null ? new byte[0] : body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }
    }

}
