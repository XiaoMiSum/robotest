package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EnvironmentConnectionTesterTest {

    @Test
    void testConnection_redisUrl_delegatesToRedisOpener() throws Exception {
        EnvironmentConnectionTester.RedisOpener opener = mock(EnvironmentConnectionTester.RedisOpener.class);
        when(opener.open("redis://localhost:6379")).thenReturn(
                new ApiDataSourceTestRespDTO(true, "OK", "Redis 7.0"));

        ApiDataSourceTestRespDTO result = EnvironmentConnectionTester.testConnection(
                "redis://localhost:6379", null, null, opener, mock(EnvironmentConnectionTester.JdbcOpener.class));

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDatabaseVersion()).contains("Redis");
    }

    @Test
    void testConnection_unsupportedDriver_throws() {
        assertThatThrownBy(() -> EnvironmentConnectionTester.testConnection(
                "jdbc:postgresql://localhost:5432/db", "org.other.Driver", null,
                mock(EnvironmentConnectionTester.RedisOpener.class), mock(EnvironmentConnectionTester.JdbcOpener.class)))
                .isInstanceOf(xyz.migoo.framework.common.exception.ServiceException.class);
    }

    @Test
    void testConnection_jdbc_delegatesToJdbcOpener() throws Exception {
        EnvironmentConnectionTester.JdbcOpener opener = mock(EnvironmentConnectionTester.JdbcOpener.class);
        Connection conn = mock(Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(meta.getDatabaseMajorVersion()).thenReturn(15);
        when(meta.getDatabaseMinorVersion()).thenReturn(0);
        when(opener.open(eq("org.postgresql.Driver"), anyString(), any(Properties.class))).thenReturn(conn);

        ApiDataSourceTestRespDTO result = EnvironmentConnectionTester.testConnection(
                "jdbc:postgresql://localhost:5432/db", "org.postgresql.Driver",
                Map.of("user", "postgres", "password", "secret"),
                mock(EnvironmentConnectionTester.RedisOpener.class), opener);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDatabaseVersion()).contains("PostgreSQL 15.0");
    }

    @Test
    void testHttp_delegatesToHttpOpener() throws Exception {
        EnvironmentConnectionTester.HttpOpener opener = mock(EnvironmentConnectionTester.HttpOpener.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(opener.get("https://example.com")).thenReturn(response);

        ApiHttpTestRespDTO result = EnvironmentConnectionTester.testHttp("https://example.com", opener);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(200);
    }
}