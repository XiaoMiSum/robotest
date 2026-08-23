package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableBatchReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentVariableRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiHttpTestRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDataSource;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiDataSourceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 环境子资源（变量/处理器）、连接测试与导入导出的行为验证 */
@ExtendWith(MockitoExtension.class)
class ApiEnvironmentSubResourceTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final String SECRET_KEY_BASE64 = "4chJwgVabFLPyA0Mty7RDhu7lXR5Hik2QZ0FJjS3rtI=";

    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ApiEnvironmentHttpMapper httpMapper;
    @Mock
    private ApiEnvironmentVariableMapper variableMapper;
    @Mock
    private ApiDataSourceMapper dataSourceMapper;
    @Mock
    private ApiEnvironmentProcessorMapper processorMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiEnvironmentServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "secretKeyBase64", SECRET_KEY_BASE64);
    }

    private void stubEnv() {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(ENV_ID);
        env.setProjectId(PROJECT_ID);
        when(environmentMapper.selectById(ENV_ID)).thenReturn(env);
    }

    private static ApiEnvironmentSaveReqDTO.Variable variable(String name, String value, String type) {
        ApiEnvironmentSaveReqDTO.Variable v = new ApiEnvironmentSaveReqDTO.Variable();
        v.setName(name);
        v.setValue(value);
        v.setType(type);
        return v;
    }

    // ==================== 变量子资源 ====================

    @Test
    void batchReplaceVariables_invalidNamePattern_throwsValidation() {
        stubEnv();
        ApiEnvironmentVariableBatchReqDTO req = new ApiEnvironmentVariableBatchReqDTO();
        req.setVariables(List.of(variable("bad-name", "1", "text")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.batchReplaceVariables(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), ex.getCode());
        verify(variableMapper, org.mockito.Mockito.never()).deleteByEnvironmentId(any());
    }

    @Test
    void batchReplaceVariables_replacesAndReturnsMaskedList() {
        stubEnv();
        ApiEnvironmentVariableBatchReqDTO req = new ApiEnvironmentVariableBatchReqDTO();
        req.setVariables(List.of(
                variable("BASE_URL", "https://x", "text"),
                variable("SECRET", "plain-pass", "sensitive")));

        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of());
        service.batchReplaceVariables(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        verify(variableMapper).deleteByEnvironmentId(ENV_ID);
        org.mockito.ArgumentCaptor<List<ApiEnvironmentVariable>> captor = org.mockito.ArgumentCaptor.captor();
        verify(variableMapper).insertBatch(captor.capture());
        String cipher = captor.getValue().stream().filter(r -> "sensitive".equals(r.getType()))
                .findFirst().orElseThrow().getValue();
        assertFalse(cipher.contains("plain-pass"));
    }

    @Test
    void addVariableFromResult_duplicateNameThrows() {
        stubEnv();
        when(variableMapper.findByEnvironmentIdAndName(ENV_ID, "orderNo")).thenReturn(new ApiEnvironmentVariable());

        ApiEnvironmentVariableCreateReqDTO req = new ApiEnvironmentVariableCreateReqDTO();
        req.setName("orderNo");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.addVariableFromResult(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req));
        assertEquals(ErrorCodeConstants.API_ENV_VARIABLE_EXISTS.code(), ex.getCode());
    }

    @Test
    void addVariableFromResult_encryptsSensitiveAndParsesSourceIds() {
        stubEnv();
        when(variableMapper.findByEnvironmentIdAndName(ENV_ID, "token")).thenReturn(null);

        ApiEnvironmentVariableCreateReqDTO req = new ApiEnvironmentVariableCreateReqDTO();
        req.setName("token");
        req.setValue("secret-value");
        req.setType("sensitive");
        req.setSourceStepId(UUID.randomUUID().toString());
        req.setSourceReportId("not-a-uuid");

        var resp = service.addVariableFromResult(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        assertEquals(ApiEnvironmentDetailRespDTO.SENSITIVE_MASK, resp.getValue());
        assertTrue(resp.getHasValue());
        org.mockito.ArgumentCaptor<ApiEnvironmentVariable> captor =
                org.mockito.ArgumentCaptor.forClass(ApiEnvironmentVariable.class);
        verify(variableMapper).insert(captor.capture());
        assertFalse(captor.getValue().getValue().contains("secret-value"));
        assertEquals(req.getSourceStepId(), captor.getValue().getSourceStepId().toString());
        assertNull(captor.getValue().getSourceReportId());
    }

    @Test
    void importVariables_countsCreatedSkippedOverwritten() {
        stubEnv();
        ApiEnvironmentVariable existing = new ApiEnvironmentVariable();
        existing.setId(UUID.randomUUID());
        existing.setName("A");
        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(existing));

        ApiEnvironmentVariableImportReqDTO req = new ApiEnvironmentVariableImportReqDTO();
        req.setVariables(List.of(variable("NEW", "1", "text"), variable("A", "2", "text")));
        req.setOverwrite(false);

        ApiEnvImportResultRespDTO result = service.importVariables(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertEquals(1, result.getCreatedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(0, result.getOverwrittenCount());

        req.setOverwrite(true);
        ApiEnvImportResultRespDTO overwriteResult =
                service.importVariables(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertEquals(1, overwriteResult.getCreatedCount());
        assertEquals(1, overwriteResult.getOverwrittenCount());
        assertEquals(0, overwriteResult.getSkippedCount());
    }

    @Test
    void exportVariables_masksSensitiveKeepsPlainText() {
        stubEnv();
        ApiEnvironmentVariable sensitive = new ApiEnvironmentVariable();
        sensitive.setId(UUID.randomUUID());
        sensitive.setName("PWD");
        sensitive.setValue("cipher");
        sensitive.setType("sensitive");
        ApiEnvironmentVariable text = new ApiEnvironmentVariable();
        text.setId(UUID.randomUUID());
        text.setName("URL");
        text.setValue("https://x");
        text.setType("text");
        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(sensitive, text));

        List<ApiEnvironmentVariableRespDTO> list =
                service.exportVariables(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        assertEquals(ApiEnvironmentDetailRespDTO.SENSITIVE_MASK,
                list.stream().filter(v -> "PWD".equals(v.getName())).findFirst().orElseThrow().getValue());
        assertEquals("https://x",
                list.stream().filter(v -> "URL".equals(v.getName())).findFirst().orElseThrow().getValue());
    }

    @Test
    void revealVariable_decryptsSensitiveForMaintainer() {
        stubEnv();
        byte[] key = io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil.parseKey(SECRET_KEY_BASE64);
        String cipher = io.github.xiaomisum.robotest.framework.util.SecretCryptoUtil.encrypt(key, "real-secret");
        ApiEnvironmentVariable row = new ApiEnvironmentVariable();
        row.setId(UUID.randomUUID());
        row.setEnvironmentId(ENV_ID);
        row.setName("PWD");
        row.setValue(cipher);
        row.setType("sensitive");
        when(variableMapper.selectById(row.getId())).thenReturn(row);

        var resp = service.revealVariable(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, row.getId());
        assertEquals("real-secret", resp.getValue());
    }

    @Test
    void revealVariable_textTypeReturnsRawValue() {
        stubEnv();
        ApiEnvironmentVariable row = new ApiEnvironmentVariable();
        row.setId(UUID.randomUUID());
        row.setEnvironmentId(ENV_ID);
        row.setName("URL");
        row.setValue("https://x");
        row.setType("text");
        when(variableMapper.selectById(row.getId())).thenReturn(row);

        var resp = service.revealVariable(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, row.getId());
        assertEquals("https://x", resp.getValue());
    }

    // ==================== 连接测试 ====================

    @Test
    void testDataSource_unsupportedDriverThrows7403WithDetail() {
        stubEnv();
        ApiDataSource ds = new ApiDataSource();
        ds.setId(UUID.randomUUID());
        ds.setEnvironmentId(ENV_ID);
        ds.setDriver("oracle.jdbc.OracleDriver");
        ds.setUrl("jdbc:oracle:thin:@host:1521/orcl");
        when(dataSourceMapper.selectById(ds.getId())).thenReturn(ds);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.testDataSource(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, ds.getId()));
        assertEquals(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的数据库驱动"));
    }

    @Test
    void testDataSource_foreignOrMissingRowThrowsNotFound() {
        // selectById 未打桩时默认返回 null，直接走「不存在」分支
        assertThrows(ServiceException.class,
                () -> service.testDataSource(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, UUID.randomUUID()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDataSource_successReturnsDatabaseVersion() throws Exception {
        stubEnv();
        ApiDataSource ds = new ApiDataSource();
        ds.setId(UUID.randomUUID());
        ds.setEnvironmentId(ENV_ID);
        ds.setDriver("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://db:5432/test");
        ds.setConnectionProperties(Map.of("user", "tester"));
        when(dataSourceMapper.selectById(ds.getId())).thenReturn(ds);

        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(meta.getDatabaseMajorVersion()).thenReturn(18);
        when(meta.getDatabaseMinorVersion()).thenReturn(2);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(meta);

        ApiEnvironmentServiceImpl spyService = org.mockito.Mockito.spy(service);
        doReturn(connection).when(spyService).openJdbcConnection(eq(ds.getDriver()), eq(ds.getUrl()), any());

        ApiDataSourceTestRespDTO resp = spyService.testDataSource(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, ds.getId());
        assertTrue(resp.getSuccess());
        assertEquals("PostgreSQL 18.2", resp.getDatabaseVersion());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDataSource_redisUrlRoutesToRespPingWithoutDriver() throws Exception {
        // 免驱动设计：driver 为空串仍按 redis:// 协议走 RESP 测试而非白名单校验
        stubEnv();
        ApiDataSource ds = new ApiDataSource();
        ds.setId(UUID.randomUUID());
        ds.setEnvironmentId(ENV_ID);
        ds.setDriver("");
        ds.setUrl("redis://:secret@cache:6379/0");
        when(dataSourceMapper.selectById(ds.getId())).thenReturn(ds);

        ApiEnvironmentServiceImpl spyService = org.mockito.Mockito.spy(service);
        doReturn(new ApiDataSourceTestRespDTO(true, "连接成功", "Redis 7.2.4"))
                .when(spyService).openRedisConnection(ds.getUrl());

        ApiDataSourceTestRespDTO resp = spyService.testDataSource(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, ds.getId());
        assertTrue(resp.getSuccess());
        assertEquals("Redis 7.2.4", resp.getDatabaseVersion());
    }

    @Test
    void testDataSource_redisConnectionFailureThrows7403WithCause() throws Exception {
        stubEnv();
        ApiDataSource ds = new ApiDataSource();
        ds.setId(UUID.randomUUID());
        ds.setEnvironmentId(ENV_ID);
        ds.setUrl("rediss://cache:6379/0");
        when(dataSourceMapper.selectById(ds.getId())).thenReturn(ds);

        ApiEnvironmentServiceImpl spyService = org.mockito.Mockito.spy(service);
        doThrow(new java.net.ConnectException("Connection refused"))
                .when(spyService).openRedisConnection(ds.getUrl());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> spyService.testDataSource(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, ds.getId()));
        assertEquals(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHttpConfig_networkFailureReturnsStructuredFalse() throws Exception {
        stubEnv();
        ApiEnvironmentHttp config = new ApiEnvironmentHttp();
        config.setId(UUID.randomUUID());
        config.setEnvironmentId(ENV_ID);
        config.setBaseUrl("https://unreachable.example.com");
        when(httpMapper.selectById(config.getId())).thenReturn(config);

        ApiEnvironmentServiceImpl spyService = org.mockito.Mockito.spy(service);
        doThrow(new java.net.ConnectException("Connection refused"))
                .when(spyService).executeHttpGet(any(ApiEnvironmentHttp.class));

        ApiHttpTestRespDTO resp = spyService.testHttpConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, config.getId());
        assertFalse(resp.getSuccess());
        assertNull(resp.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHttpConfig_httpResponseCountsAsSuccess() throws Exception {
        stubEnv();
        ApiEnvironmentHttp config = new ApiEnvironmentHttp();
        config.setId(UUID.randomUUID());
        config.setEnvironmentId(ENV_ID);
        config.setBaseUrl("https://api.example.com");
        when(httpMapper.selectById(config.getId())).thenReturn(config);

        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        ApiEnvironmentServiceImpl spyService = org.mockito.Mockito.spy(service);
        doReturn(response).when(spyService).executeHttpGet(any(ApiEnvironmentHttp.class));

        ApiHttpTestRespDTO resp = spyService.testHttpConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, config.getId());
        assertTrue(resp.getSuccess());
        assertEquals(404, resp.getStatusCode());
    }

    // ==================== 环境导入导出 ====================

    @Test
    void importEnvironment_createsWhenAbsent() {
        
        ApiEnvironmentDetailRespDTO payload = new ApiEnvironmentDetailRespDTO();
        payload.setName("导入环境");
        payload.setDescription("来自导出文件");
        ApiEnvironmentDetailRespDTO.Variable text = new ApiEnvironmentDetailRespDTO.Variable();
        text.setName("K");
        text.setValue("v");
        text.setType("text");
        payload.setVariables(List.of(text));
        MockMultipartFile file = multipartFile(JsonUtils.toJsonString(payload));

        when(environmentMapper.findByProjectIdAndName(PROJECT_ID, "导入环境")).thenReturn(null);
        when(environmentMapper.listByProject(PROJECT_ID, null)).thenReturn(List.of());

        ApiEnvImportResultRespDTO result =
                service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, file, false);
        assertEquals(1, result.getCreatedCount());
        verify(environmentMapper).insert(any(ApiEnvironment.class));
    }

    @Test
    void importEnvironment_skipsExistingWhenOverwriteOff() {
        
        ApiEnvironmentDetailRespDTO payload = new ApiEnvironmentDetailRespDTO();
        payload.setName("已有环境");
        MockMultipartFile file = multipartFile(JsonUtils.toJsonString(payload));

        when(environmentMapper.findByProjectIdAndName(PROJECT_ID, "已有环境")).thenReturn(new ApiEnvironment());

        ApiEnvImportResultRespDTO result =
                service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, file, false);
        assertEquals(1, result.getSkippedCount());
        verify(environmentMapper, org.mockito.Mockito.never()).insert(any(ApiEnvironment.class));
    }

    @Test
    void importEnvironment_invalidJsonThrowsValidation() {
        // JSON 解析失败发生在访问环境之前，无需环境桩
        MockMultipartFile file = multipartFile("{ not json");
        assertThrows(ServiceException.class,
                () -> service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, file, false));
    }

    private static MockMultipartFile multipartFile(String content) {
        return new MockMultipartFile("file", "env.json",
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE, content.getBytes());
    }
}
