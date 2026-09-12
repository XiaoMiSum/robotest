package io.github.xiaomisum.robotest.service.apitest.imports;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** URL 拉取器：SSRF 防护两级策略（strict 禁内网 / intranet 放行内网但限 swagger 路径） */
class ImportSourceFetcherTest {

    private final ImportSourceFetcher strict = new ImportSourceFetcher(ImportSourceFetcher.Policy.STRICT);
    private final ImportSourceFetcher intranet = new ImportSourceFetcher(ImportSourceFetcher.Policy.INTRANET);

    // region strict：协议白名单 + 内网/保留地址黑名单

    @Test
    void rejectsNonHttpProtocols() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("ftp://example.com/openapi.json"));
        assertThat(exception.getMessage()).contains("仅允许 http/https");
    }

    @Test
    void rejectsUrlWithoutHost() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http:///path/only"));
        assertThat(exception.getMessage()).contains("缺少主机名");
    }

    @Test
    void rejectsLoopbackAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://127.0.0.1:8080/swagger.json"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsSiteLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://10.1.2.3/openapi.json"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsLinkLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://169.254.1.9/metadata"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsAnyLocalAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://0.0.0.0/admin"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsMulticastAddress() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://224.0.0.1/group"));
        assertThat(exception.getMessage()).contains("禁止访问内网或保留地址");
    }

    @Test
    void rejectsUnresolvableHost() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> strict.fetch("http://this-host-does-not-exist.invalid/openapi.json"));
        assertThat(exception.getMessage()).contains("域名解析失败");
    }

    // endregion

    // region intranet：放行内网/回环，仍禁高危地址，仅允许 swagger 文档特征路径

    @Test
    void intranetRejectsMetadataLinkLocal() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> intranet.fetch("http://169.254.169.254/v3/api-docs"));
        assertThat(exception.getMessage()).contains("禁止访问链路本地、组播或保留地址");
    }

    @Test
    void intranetRejectsNonSwaggerPath() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> intranet.fetch("http://10.1.2.3/admin"));
        assertThat(exception.getMessage()).contains("仅允许访问 swagger 文档路径");
    }

    @Test
    void intranetAllowsLoopbackWithSwaggerV3Path() {
        assertThatCode(() -> intranet.guard(java.net.URI.create("http://127.0.0.1:58080/v3/api-docs")))
                .doesNotThrowAnyException();
    }

    @Test
    void intranetAllowsSiteLocalWithJsonSuffix() {
        assertThatCode(() -> intranet.guard(java.net.URI.create("http://10.1.2.3/swagger/openapi.json")))
                .doesNotThrowAnyException();
    }

    @Test
    void intranetAllowsContextPrefixSwaggerPath() {
        assertThatCode(() -> intranet.guard(java.net.URI.create("http://10.1.2.3/myapp/swagger.json")))
                .doesNotThrowAnyException();
    }

    // endregion
}