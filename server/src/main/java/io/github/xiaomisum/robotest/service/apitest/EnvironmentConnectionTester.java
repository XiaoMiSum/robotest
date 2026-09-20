package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 环境连接测试核心（接口测试域重构方案 04 §4.2，详细设计 3.1.7/3.1.8）。
 * <p>
 * 静态编排：测试动作通过函数式接缝注入（真实 I/O 由 openRedis/openJdbc/httpGet 提供，可测接缝保留在服务层），
 * 由调用方以方法引用传入，单测 spy 仍可命中。请求体传配置不落库，仅返回连通性结果。 */
public final class EnvironmentConnectionTester {

    private EnvironmentConnectionTester() {
    }

    /** 仅放行随服务打包的驱动，防止任意类加载（安全约束） */
    public static final Set<String> SUPPORTED_JDBC_DRIVERS =
            Set.of("org.postgresql.Driver", "com.mysql.cj.jdbc.Driver");
    public static final int JDBC_LOGIN_TIMEOUT_SECONDS = 10;
    /** PING 建连超时，与 JDBC 登录超时口径一致 */
    public static final Duration REDIS_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Redis 建连接缝：真实 RESP 建连逻辑在服务层实现 */
    @FunctionalInterface
    public interface RedisOpener {
        ApiDataSourceTestRespDTO open(String url) throws Exception;
    }

    /** JDBC 建连接缝：真实驱动加载/建连逻辑在服务层实现 */
    @FunctionalInterface
    public interface JdbcOpener {
        Connection open(String driver, String url, Properties props) throws Exception;
    }

    /** HTTP GET 接缝：真实 HttpClient 逻辑在服务层实现 */
    @FunctionalInterface
    public interface HttpOpener {
        HttpResponse<Void> get(String baseUrl) throws Exception;
    }

    /** 真实 Redis RESP 建连：PING + server info 取版本号 */
    public static ApiDataSourceTestRespDTO openRedis(String url) throws Exception {
        RedisURI uri = RedisURI.create(url);
        uri.setTimeout(REDIS_CONNECT_TIMEOUT);
        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> connection = client.connect();
        try {
            connection.sync().ping();
            String info = connection.sync().info("server");
            String redisVersion = null;
            if (info != null) {
                for (String line : info.split("\r?\n")) {
                    if (line.startsWith("redis_version:")) {
                        redisVersion = line.substring("redis_version:".length()).trim();
                        break;
                    }
                }
            }
            return new ApiDataSourceTestRespDTO(true, "连接成功",
                    redisVersion == null ? null : "Redis " + redisVersion);
        } finally {
            connection.close();
            client.shutdown();
        }
    }

    /** 真实 JDBC 建连：按白名单驱动加载并设置登录超时 */
    public static Connection openJdbc(String driver, String url, Properties props) throws Exception {
        Class.forName(driver);
        DriverManager.setLoginTimeout(JDBC_LOGIN_TIMEOUT_SECONDS);
        return DriverManager.getConnection(url, props);
    }

    /** 真实 HTTP GET：连接超时 10s、响应超时 30s，跟随重定向 */
    public static HttpResponse<Void> httpGet(String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofMillis(30000))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    /** 3.1.7 连接判定核心：Redis 按 URL 协议走 RESP，JDBC 仅放行内置驱动 */
    public static ApiDataSourceTestRespDTO testConnection(String url, String driver,
            Map<String, Object> connectionProperties, RedisOpener redisOpener, JdbcOpener jdbcOpener) {
        if (url != null && (url.startsWith("redis://") || url.startsWith("rediss://"))) {
            try {
                return redisOpener.open(url);
            } catch (Exception e) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        if (!SUPPORTED_JDBC_DRIVERS.contains(driver)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    "不支持的数据库驱动：" + driver);
        }

        Properties props = new Properties();
        if (connectionProperties != null) {
            for (Map.Entry<String, Object> entry : connectionProperties.entrySet()) {
                if (entry.getValue() != null && entry.getKey() instanceof String keyName) {
                    props.put(keyName, String.valueOf(entry.getValue()));
                }
            }
        }
        try (Connection connection = jdbcOpener.open(driver, url, props)) {
            DatabaseMetaData meta = connection.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseMajorVersion()
                    + "." + meta.getDatabaseMinorVersion();
            return new ApiDataSourceTestRespDTO(true, "连接成功", version);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** 3.1.8 HTTP 连通性：任意 HTTP 响应均视为连通（含 4xx/5xx），网络层失败才走 success=false 分支 */
    public static ApiHttpTestRespDTO testHttp(String baseUrl, HttpOpener httpOpener) {
        long start = System.currentTimeMillis();
        try {
            HttpResponse<Void> response = httpOpener.get(baseUrl);
            long durationMs = System.currentTimeMillis() - start;
            return new ApiHttpTestRespDTO(true, "连接成功", response.statusCode(), durationMs);
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            String message = e.getCause() != null && e.getCause().getMessage() != null
                    ? e.getCause().getMessage() : e.getMessage();
            return new ApiHttpTestRespDTO(false,
                    message == null ? e.getClass().getSimpleName() : message, null, durationMs);
        }
    }
}