package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDataSourceTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentVariableCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiHttpConfigTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDataSourceTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiEnvironmentServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ENV_ID = UUID.randomUUID();

    @Mock
    private ApiEnvironmentMapper environmentMapper;
    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiEnvironmentServiceImpl service;

    private void stubExistingEnv(List<Map<String, Object>> variables, List<Map<String, Object>> dataSources) {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(ENV_ID);
        env.setProjectId(PROJECT_ID);
        env.setName("测试环境");
        env.setScope("project");
        env.setIsDefault(true);
        env.setSortOrder(0);
        env.setHttpConfigs(httpConfig("https://staging.example.com"));
        env.setVariables(new ArrayList<>(variables));
        env.setDataSources(new ArrayList<>(dataSources));
        env.setProcessors(List.of());
        when(environmentMapper.selectById(ENV_ID)).thenReturn(env);
    }

    private static List<Map<String, Object>> httpConfig(String baseUrl) {
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("name", "内部 API");
        http.put("refName", "http_1");
        http.put("baseUrl", baseUrl);
        http.put("headers", List.of());
        return new ArrayList<>(List.of(http));
    }

    private static ApiEnvironmentSaveReqDTO fullReq() {
        ApiEnvironmentSaveReqDTO req = new ApiEnvironmentSaveReqDTO();
        req.setName("测试环境");
        req.setDescription("日常回归");
        req.setIsDefault(false);

        ApiEnvironmentSaveReqDTO.HttpConfig http = new ApiEnvironmentSaveReqDTO.HttpConfig();
        http.setName("内部 API");
        http.setBaseUrl("https://staging.example.com");
        req.setHttpConfigs(List.of(http));

        req.setVariables(List.of(variable("BASE_URL", "https://staging.example.com"), variable("TEST_PASSWORD", "123456")));

        ApiEnvironmentSaveReqDTO.DataSource ds = new ApiEnvironmentSaveReqDTO.DataSource();
        ds.setName("测试库");
        ds.setRefName("test_db");
        ds.setDriver("org.postgresql.Driver");
        ds.setUrl("jdbc:postgresql://db:5432/test");
        req.setDataSources(List.of(ds));

        ApiEnvironmentSaveReqDTO.Processor processor = new ApiEnvironmentSaveReqDTO.Processor();
        processor.setProcessorType("preprocessor");
        processor.setName("Token 预置");
        processor.setConfig(Map.of("processorType", "groovy"));
        req.setProcessors(List.of(processor));
        return req;
    }

    private static ApiEnvironmentSaveReqDTO.Variable variable(String name, String value) {
        ApiEnvironmentSaveReqDTO.Variable v = new ApiEnvironmentSaveReqDTO.Variable();
        v.setName(name);
        v.setValue(value);
        return v;
    }

    private ApiEnvironment capturedInsert() {
        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== 创建 ====================

    @Test
    void createEnvironment_nameDuplicated_throws() {
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(true);

        assertThrows(ServiceException.class,
                () -> service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, fullReq()));
    }

    @Test
    void createEnvironment_withoutHttpConfig_generatesDefault() {
        ApiEnvironmentSaveReqDTO req = fullReq();
        req.setHttpConfigs(null);
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        ApiEnvironment env = capturedInsert();
        assertEquals("project", env.getScope());
        assertEquals(1, env.getHttpConfigs().size());
        assertEquals("默认配置", env.getHttpConfigs().get(0).get("name"));
    }

    @Test
    void createEnvironment_persistsVariablesPlaintext() {
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, fullReq());

        ApiEnvironment env = capturedInsert();
        assertEquals("123456", variableValue(env, "TEST_PASSWORD"));
        assertEquals("https://staging.example.com", variableValue(env, "BASE_URL"));
    }

    @Test
    void createEnvironment_duplicateVariableName_throwsValidation() {
        ApiEnvironmentSaveReqDTO req = fullReq();
        req.setVariables(List.of(variable("A", "1"), variable("A", "2")));
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, req));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), ex.getCode());
    }

    @Test
    void createEnvironment_invalidProcessorType_throwsValidation() {
        ApiEnvironmentSaveReqDTO req = fullReq();
        ApiEnvironmentSaveReqDTO.Processor processor = new ApiEnvironmentSaveReqDTO.Processor();
        processor.setProcessorType("middleware");
        processor.setName("非法处理器");
        processor.setConfig(Map.of());
        req.setProcessors(List.of(processor));
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, req));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), ex.getCode());
    }

    @Test
    void createEnvironment_asDefault_clearsPreviousDefault() {
        ApiEnvironmentSaveReqDTO req = fullReq();
        req.setIsDefault(true);
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, req);

        verify(environmentMapper).clearDefaultByProjectId(PROJECT_ID);
    }

    // ==================== 更新与删除 ====================

    @Test
    void updateEnvironment_notFoundOrForeignProject_throws() {
        when(environmentMapper.selectById(ENV_ID)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> service.updateEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, fullReq()));
    }

    @Test
    void updateEnvironment_promoteToDefault_clearsOthersAndReplacesAggregate() {
        stubExistingEnv(List.of(), List.of());
        ApiEnvironment existing = environmentMapper.selectById(ENV_ID);
        existing.setIsDefault(false);
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", ENV_ID)).thenReturn(false);

        ApiEnvironmentSaveReqDTO req = fullReq();
        req.setIsDefault(true);
        service.updateEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        verify(environmentMapper).clearDefaultByProjectId(PROJECT_ID);
        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(captor.capture());
        ApiEnvironment update = captor.getValue();
        // 聚合资源整批替换写入主表 JSONB
        assertEquals(1, update.getHttpConfigs().size());
        assertEquals(2, update.getVariables().size());
        assertEquals(1, update.getProcessors().size());
        assertEquals(1, update.getDataSources().size());
    }

    @Test
    void updateEnvironment_blankVariableValueStoredAsIs() {
        stubExistingEnv(List.of(), List.of());
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", ENV_ID)).thenReturn(false);

        ApiEnvironmentSaveReqDTO req = fullReq();
        req.getVariables().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow().setValue(null);
        service.updateEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(captor.capture());
        assertNull(captor.getValue().getVariables().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.get("name"))).findFirst().orElseThrow().get("value"));
    }

    @Test
    void deleteEnvironment_deletesEnvOnly() {
        stubExistingEnv(List.of(), List.of());

        service.deleteEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        verify(environmentMapper).deleteById(ENV_ID);
    }

    // ==================== 设默认 / 排序 ====================

    @Test
    void setDefaultEnvironment_clearsAllThenMarksTarget() {
        stubExistingEnv(List.of(), List.of());

        var resp = service.setDefaultEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        assertTrue(resp.getSuccess());
        verify(environmentMapper).clearDefaultByProjectId(PROJECT_ID);
        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(captor.capture());
        assertEquals(ENV_ID, captor.getValue().getId());
        assertTrue(captor.getValue().getIsDefault());
    }

    @Test
    void sortEnvironment_updatesSortOrderOnly() {
        stubExistingEnv(List.of(), List.of());

        ApiEnvironmentSortReqDTO req = new ApiEnvironmentSortReqDTO();
        req.setSortOrder(3);
        service.sortEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(captor.capture());
        assertEquals(ENV_ID, captor.getValue().getId());
        assertEquals(3, captor.getValue().getSortOrder());
        assertNull(captor.getValue().getName());
    }

    // ==================== 详情与复制 ====================

    @Test
    void getEnvironment_returnsVariablePlaintext() {
        List<Map<String, Object>> variables = new ArrayList<>();
        variables.add(variableRow("TEST_PASSWORD", "123456"));
        variables.add(variableRow("BASE_URL", "https://staging.example.com"));
        stubExistingEnv(variables, List.of());

        ApiEnvironmentDetailRespDTO detail = service.getEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        ApiEnvironmentDetailRespDTO.Variable pwd = detail.getVariables().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow();
        assertEquals("123456", pwd.getValue());
        assertTrue(pwd.getHasValue());
        assertEquals("https://staging.example.com", detail.getVariables().stream()
                .filter(v -> "BASE_URL".equals(v.getName())).findFirst().orElseThrow().getValue());
    }

    @Test
    void copyEnvironment_copiesVariablesWithValuesNoDataSources() {
        List<Map<String, Object>> variables = new ArrayList<>();
        variables.add(variableRow("TOKEN", "abc123"));
        variables.add(variableRow("BASE_URL", "https://x"));
        List<Map<String, Object>> dataSources = new ArrayList<>();
        dataSources.add(dataSourceRow());
        stubExistingEnv(variables, dataSources);
        when(environmentMapper.listByProject(PROJECT_ID, null)).thenReturn(List.of());
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "预发环境（副本）", null)).thenReturn(false);

        ApiEnvironmentCopyReqDTO req = new ApiEnvironmentCopyReqDTO();
        req.setName("预发环境（副本）");
        service.copyEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        // 数据源不复制（详细设计 3.1.11）
        ApiEnvironment copy = capturedInsert();
        assertTrue(copy.getDataSources().isEmpty());
        // 变量随副本写入主表 JSONB 且保留取值
        assertEquals(2, copy.getVariables().size());
        assertEquals("abc123", copy.getVariables().stream()
                .filter(v -> "TOKEN".equals(v.get("name"))).findFirst().orElseThrow().get("value"));
    }

    private static Map<String, Object> variableRow(String name, String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("value", value);
        row.put("description", null);
        return row;
    }

    private static Map<String, Object> dataSourceRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "测试库");
        row.put("refName", "test_db");
        row.put("driver", "org.postgresql.Driver");
        row.put("url", "jdbc:postgresql://db:5432/test");
        return row;
    }

    private static String variableValue(ApiEnvironment env, String name) {
        return env.getVariables().stream()
                .filter(v -> name.equals(v.get("name"))).findFirst().orElseThrow()
                .get("value").toString();
    }

    /** 空聚合环境：仅承载 id/projectId，JSONB 列表为空，供导出/连接测试/添加变量使用 */
    private void stubEmptyEnv() {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(ENV_ID);
        env.setProjectId(PROJECT_ID);
        env.setHttpConfigs(List.of());
        env.setVariables(new ArrayList<>());
        env.setDataSources(new ArrayList<>());
        env.setProcessors(List.of());
        when(environmentMapper.selectById(ENV_ID)).thenReturn(env);
    }

    // ==================== 导出（3.1.9） ====================

    @Test
    void exportEnvironment_excludesDataSourcesForMasking() {
        List<Map<String, Object>> variables = new ArrayList<>();
        variables.add(variableRow("K", "v"));
        stubExistingEnv(variables, List.of(dataSourceRow()));

        var detail = service.exportEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        // 凭据内嵌于连接 URL 无法部分脱敏，导出必须整段排除数据源（需求 3.7.1 导出脱敏）
        assertTrue(detail.getDataSources().isEmpty());
        assertEquals(1, detail.getVariables().size());
    }

    // ==================== 添加变量（3.3.2） ====================

    @Test
    void addVariableFromResult_duplicateNameThrows() {
        stubEmptyEnv();
        ApiEnvironment env = environmentMapper.selectById(ENV_ID);
        env.getVariables().add(variableRow("orderNo", "1"));

        ApiEnvironmentVariableCreateReqDTO req = new ApiEnvironmentVariableCreateReqDTO();
        req.setName("orderNo");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.addVariableFromResult(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req));
        assertEquals(ErrorCodeConstants.API_ENV_VARIABLE_EXISTS.code(), ex.getCode());
    }

    @Test
    void addVariableFromResult_persistsPlaintextAndParsesSourceIds() {
        stubEmptyEnv();

        ApiEnvironmentVariableCreateReqDTO req = new ApiEnvironmentVariableCreateReqDTO();
        req.setName("token");
        req.setValue("secret-value");
        req.setSourceStepId(UUID.randomUUID().toString());
        req.setSourceReportId("not-a-uuid");

        var resp = service.addVariableFromResult(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        assertEquals("secret-value", resp.getValue());
        assertTrue(resp.getHasValue());
        ArgumentCaptor<ApiEnvironment> captor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).updateById(captor.capture());
        Map<String, Object> saved = captor.getValue().getVariables().get(0);
        assertEquals("secret-value", saved.get("value"));
        assertEquals(req.getSourceStepId(), saved.get("sourceStepId").toString());
        assertNull(saved.get("sourceReportId"));
    }

    // ==================== 连接测试（3.1.7 / 3.1.8） ====================

    @SuppressWarnings("unchecked")
    @Test
    void testDataSourceConfig_unsavedPayloadRoutesToJdbc() throws Exception {
        stubEmptyEnv();
        ApiDataSourceTestReqDTO req = new ApiDataSourceTestReqDTO();
        req.setDriver("com.mysql.cj.jdbc.Driver");
        req.setUrl("jdbc:mysql://db:3306/test");
        req.setConnectionProperties(Map.of("user", "tester"));

        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(meta.getDatabaseMajorVersion()).thenReturn(8);
        when(meta.getDatabaseMinorVersion()).thenReturn(0);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(meta);

        ApiEnvironmentServiceImpl spyService = spy(service);
        doReturn(connection).when(spyService).openJdbcConnection(eq(req.getDriver()), eq(req.getUrl()), any());

        ApiDataSourceTestRespDTO resp = spyService.testDataSourceConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertTrue(resp.getSuccess());
        assertEquals("MySQL 8.0", resp.getDatabaseVersion());
    }

    @Test
    void testDataSourceConfig_unsavedRedisPayloadBypassesDriverWhitelist() throws Exception {
        stubEmptyEnv();
        ApiDataSourceTestReqDTO req = new ApiDataSourceTestReqDTO();
        req.setDriver("");
        req.setUrl("redis://:secret@cache:6379/0");

        ApiEnvironmentServiceImpl spyService = spy(service);
        doReturn(new ApiDataSourceTestRespDTO(true, "连接成功", "Redis 7.2.4"))
                .when(spyService).openRedisConnection(req.getUrl());

        ApiDataSourceTestRespDTO resp = spyService.testDataSourceConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertTrue(resp.getSuccess());
        assertEquals("Redis 7.2.4", resp.getDatabaseVersion());
    }

    @Test
    void testDataSourceConfig_unsupportedDriverThrows7403WithDetail() {
        stubEmptyEnv();
        ApiDataSourceTestReqDTO req = new ApiDataSourceTestReqDTO();
        req.setDriver("oracle.jdbc.OracleDriver");
        req.setUrl("jdbc:oracle:thin:@host:1521/orcl");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.testDataSourceConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req));
        assertEquals(ErrorCodeConstants.API_DATASOURCE_CONN_FAILED.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的数据库驱动"));
    }

    @Test
    void testHttpConfig_networkFailureReturnsStructuredFalse() throws Exception {
        stubEmptyEnv();
        ApiHttpConfigTestReqDTO req = new ApiHttpConfigTestReqDTO();
        req.setBaseUrl("https://unreachable.example.com");

        ApiEnvironmentServiceImpl spyService = spy(service);
        doThrow(new java.net.ConnectException("Connection refused"))
                .when(spyService).executeHttpGet(req.getBaseUrl());

        var resp = spyService.testHttpConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertFalse(resp.getSuccess());
        assertNull(resp.getStatusCode());
    }

    @Test
    void testHttpConfig_httpResponseCountsAsSuccess() throws Exception {
        stubEmptyEnv();
        ApiHttpConfigTestReqDTO req = new ApiHttpConfigTestReqDTO();
        req.setBaseUrl("https://api.example.com");

        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        ApiEnvironmentServiceImpl spyService = spy(service);
        doReturn(response).when(spyService).executeHttpGet(req.getBaseUrl());

        var resp = spyService.testHttpConfig(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);
        assertTrue(resp.getSuccess());
        assertEquals(404, resp.getStatusCode());
    }

    // ==================== 环境导入（3.1.10） ====================

    @Test
    void importEnvironment_createsWhenAbsent() {
        ApiEnvironmentDetailRespDTO payload = new ApiEnvironmentDetailRespDTO();
        payload.setName("导入环境");
        payload.setDescription("来自导出文件");
        ApiEnvironmentDetailRespDTO.Variable text = new ApiEnvironmentDetailRespDTO.Variable();
        text.setName("K");
        text.setValue("v");
        payload.setVariables(List.of(text));
        when(environmentMapper.findByProjectIdAndName(PROJECT_ID, "导入环境")).thenReturn(null);
        when(environmentMapper.listByProject(PROJECT_ID, null)).thenReturn(List.of());

        ApiEnvImportResultRespDTO result = service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID,
                multipartFile(JsonUtils.toJsonString(payload)), false);

        assertEquals(1, result.getCreatedCount());
        verify(environmentMapper).insert(any(ApiEnvironment.class));
    }

    @Test
    void importEnvironment_skipsExistingWhenOverwriteOff() {
        ApiEnvironmentDetailRespDTO payload = new ApiEnvironmentDetailRespDTO();
        payload.setName("已有环境");
        when(environmentMapper.findByProjectIdAndName(PROJECT_ID, "已有环境")).thenReturn(new ApiEnvironment());

        ApiEnvImportResultRespDTO result = service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID,
                multipartFile(JsonUtils.toJsonString(payload)), false);

        assertEquals(1, result.getSkippedCount());
        verify(environmentMapper, org.mockito.Mockito.never()).insert(any(ApiEnvironment.class));
    }

    @Test
    void importEnvironment_invalidJsonThrowsValidation() {
        assertThrows(ServiceException.class,
                () -> service.importEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, multipartFile("{ not json"), false));
    }

    private static MockMultipartFile multipartFile(String content) {
        return new MockMultipartFile("file", "env.json",
                org.springframework.http.MediaType.APPLICATION_JSON_VALUE, content.getBytes());
    }
}
