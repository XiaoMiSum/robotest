package io.github.xiaomisum.robotest.service.apitest.imports;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** URL 拉取器：SSRF 防护（协议白名单 + 内网黑名单）拒绝路径验证 */
class ImportSourceFetcherTest {

    private final ImportSourceFetcher fetcher = new ImportSourceFetcher();

    @Test
    void rejectsNonHttpProtocols() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("ftp://example.com/openapi.json"));
        assertThat(exception.getMessage()).contains("仅允许 http/https");
    }

    @Test
    void rejectsUrlWithoutHost() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http:///path/only"));
        assertThat(exception.getMessage()).contains("缺少主机名");
    }

    @Test
    void rejectsLoopbackAddress() {
        // 127.0.0.1 为 IP 字面量，getAllByName 免 DNS 直接复核
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://127.0.0.1:8080/swagger.json"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsSiteLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://10.1.2.3/openapi.json"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsLinkLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://169.254.1.9/metadata"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsAnyLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://0.0.0.0/admin"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsMulticastAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://224.0.0.1/group"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsUnresolvableHost() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> fetcher.fetch("http://this-host-does-not-exist.invalid/openapi.json"));
        assertThat(exception.getMessage()).contains("域名解析失败");
    }
}
