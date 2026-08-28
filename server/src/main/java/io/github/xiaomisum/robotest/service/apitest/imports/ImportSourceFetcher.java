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

import org.springframework.stereotype.Component;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.API_IMPORT_URL_UNREACHABLE;

/**
 * Swagger URL 拉取（接口管理详细设计 4.3 SSRF 防护）：
 * 协议白名单 → 内网/链路本地地址黑名单 → DNS 解析后复核 → 10s 超时
 */
@Component
public class ImportSourceFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public String fetch(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "仅允许 http/https 协议");
            }
        } catch (IllegalArgumentException exception) {
            throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "URL 格式非法");
        }
        resolveAndGuard(uri);
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

    /** 解析域名后逐个 IP 复核，防止 DNS 重绑定绕过黑名单 */
    private void resolveAndGuard(URI uri) {
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
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                throw ServiceExceptionUtil.get(API_IMPORT_URL_UNREACHABLE, "禁止访问内网或保留地址");
            }
        }
    }
}
