package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.ryze.protocol.http.RealHTTPRequest;
import org.apache.hc.core5.http.Header;
import xyz.migoo.simplehttp.Request;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 从信封 JSON 还原的 HTTP 请求（GitLab 集成详细设计 4.2 第 4 步，WP-7.1）。
 * <p>
 * {@link RealHTTPRequest} 只能从 simplehttp 执行期 exchange 快照构造，信封回放没有执行现场；
 * 这里继承它并覆盖全部读取面，使 {@link SnapshotVisitor}/{@link RyzeResultSnapshotConverter}
 * 的 {@code instanceof RealHTTPRequest} 分支无需改动即可渲染 url / method / query / headers / body。
 * 父类构造所需的 {@link Request} 仅为占位（exchange 为 null，父字段全空，读取一律走覆盖实现）。
 */
final class RecoveredHttpRequest extends RealHTTPRequest {

    private final String url;
    private final String method;
    private final String query;
    private final byte[] body;
    private final String version;
    private final List<Header> headers;

    RecoveredHttpRequest(String url, String method, String query, byte[] body,
            String version, List<Header> headers) {
        super(Request.create("GET", "http://localhost"));
        this.url = url;
        this.method = method;
        this.query = query;
        this.body = body;
        this.version = version;
        this.headers = headers;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public List<Header> getHeaders() {
        return headers;
    }

    @Override
    public byte[] bytes() {
        return body != null && body.length > 0 ? body
                : query != null ? query.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    /** 格式化口径对齐父类 {@code RealHTTPRequest#format()}：请求行 + 头 + 查询 + 体 */
    @Override
    public String format() {
        StringBuilder buf = new StringBuilder();
        buf.append(method).append(" ").append(url);
        if (version != null && !version.isBlank()) {
            buf.append(" ").append(version);
        }
        if (headers != null && !headers.isEmpty()) {
            buf.append("\n");
            headers.forEach(header -> buf.append(header.getName()).append(": ")
                    .append(header.getValue()).append("\n"));
        }
        if (query != null && !query.isBlank()) {
            buf.append("\nRequest Query:").append(query);
        }
        if (body != null && body.length > 0 && !"null".equals(new String(body, StandardCharsets.UTF_8))) {
            buf.append("\nRequest Body:").append(new String(body, StandardCharsets.UTF_8));
        }
        return buf.toString();
    }
}
