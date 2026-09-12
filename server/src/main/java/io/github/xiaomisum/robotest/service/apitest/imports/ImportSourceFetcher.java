package io.github.xiaomisum.robotest.service.apitest.imports;

import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_IMPORT_URL_UNREACHABLE;

/**
 * Swagger URL 拉取（接口管理详细设计 4.3 SSRF 防护）：
 * 协议白名单 → 按策略复核目标地址（strict 禁内网 / intranet 放行内网但限 swagger 文档路径）→ DNS 解析后复核 → 10s 超时
 */
@Component
public class ImportSourceFetcher {

    /** URL 拉取安全策略（接口管理详细设计 4.3） */
    public enum Policy {
        /** 公网/云上部署（默认）：禁止内网与全部保留地址 */
        STRICT,
        /** 内网测试环境：放行内网/回环，仍禁云元数据地址，仅允许 swagger 文档特征路径 */
        INTRANET;

        public static Policy of(String value) {
            return "intranet".equalsIgnoreCase(value == null ? "" : value.trim()) ? INTRANET : STRICT;
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** intranet 策略下允许访问的 swagger 文档特征路径（支持任意 contextPath 前缀） */
    private static final List<String> SWAGGER_DOC_ENDPOINTS = List.of(
            "/v2/api-docs", "/v3/api-docs", "/swagger.json", "/swagger.yaml", "/swagger.yml",
            "/openapi.json", "/openapi.yaml", "/openapi.yml", "/swagger-resources",
            "/swagger-ui.json", "/swagger-ui.html");

    public static final String MSG_INTRANET_HIGH_RISK_BLOCKED = "禁止访问链路本地、组播或保留地址";

    private final Policy policy;

    /** Spring 注入：策略来自配置 robotest.api-test.import.url-policy（默认 strict） */
    @Autowired
    public ImportSourceFetcher(@Value("${robotest.api-test.import.url-policy:strict}") String policy) {
        this(Policy.of(policy));
    }

    /** 单测直接指定策略构造（同包可见） */
    ImportSourceFetcher(Policy policy) {
        this.policy = policy;
    }

    public String fetch(String url) {
        URI uri = parseUri(url);
        guard(uri);
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "HTTP " + response.statusCode());
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, cause.getMessage());
        }
    }

    private URI parseUri(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "仅允许 http/https 协议");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "URL 格式非法");
        }
    }

    /** 解析域名后逐个 IP 复核（防 DNS 重绑定），并按策略放行/拒绝（同包可见便于单测） */
    void guard(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "缺少主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception exception) {
            throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "域名解析失败：" + host);
        }
        for (InetAddress address : addresses) {
            if (policy == Policy.STRICT) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "禁止访问内网或保留地址");
                }
            } else {
                if (address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, MSG_INTRANET_HIGH_RISK_BLOCKED);
                }
            }
        }
        if (policy == Policy.INTRANET) {
            requireSwaggerDocPath(uri);
        }
    }

    /** intranet 策略：仅允许访问 swagger 文档特征路径，避免把服务端变成内网资产扫描器 */
    private void requireSwaggerDocPath(URI uri) {
        String path = uri.getPath();
        String normalized = path == null ? "" : (path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        for (String endpoint : SWAGGER_DOC_ENDPOINTS) {
            if (normalized.equals(endpoint) || normalized.endsWith(endpoint)) {
                return;
            }
        }
        if (normalized.endsWith(".json") || normalized.endsWith(".yaml") || normalized.endsWith(".yml")) {
            return;
        }
        throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "仅允许访问 swagger 文档路径（如 /v3/api-docs、.json/.yaml 文件）");
    }
}