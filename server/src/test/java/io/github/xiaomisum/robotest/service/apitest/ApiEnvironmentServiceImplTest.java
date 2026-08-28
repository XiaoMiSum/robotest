package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentCopyReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiEnvironmentSortReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiEnvironmentDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiDataSource;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentHttp;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentProcessor;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironmentVariable;
import io.github.xiaomisum.robotest.repository.apitest.ApiDataSourceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentHttpMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentProcessorMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    private ApiEnvironmentHttpMapper httpMapper;
    @Mock
    private ApiEnvironmentVariableMapper variableMapper;
    @Mock
    private ApiDataSourceMapper dataSourceMapper;
    @Mock
    private ApiEnvironmentProcessorMapper processorMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;

    @InjectMocks
    private ApiEnvironmentServiceImpl service;

    @Captor
    private ArgumentCaptor<List<ApiEnvironmentHttp>> httpListCaptor;
    @Captor
    private ArgumentCaptor<List<ApiEnvironmentVariable>> varListCaptor;

    private void stubExistingEnv() {
        ApiEnvironment env = new ApiEnvironment();
        env.setId(ENV_ID);
        env.setProjectId(PROJECT_ID);
        env.setName("测试环境");
        env.setScope("project");
        env.setIsDefault(true);
        env.setSortOrder(0);
        when(environmentMapper.selectById(ENV_ID)).thenReturn(env);
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

        ApiEnvironmentSaveReqDTO.Variable base = variable("BASE_URL", "https://staging.example.com");
        ApiEnvironmentSaveReqDTO.Variable password = variable("TEST_PASSWORD", "123456");
        req.setVariables(List.of(base, password));

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

        ArgumentCaptor<ApiEnvironment> envCaptor = ArgumentCaptor.forClass(ApiEnvironment.class);
        verify(environmentMapper).insert(envCaptor.capture());
        assertEquals("project", envCaptor.getValue().getScope());

        verify(httpMapper).insertBatch(httpListCaptor.capture());
        List<ApiEnvironmentHttp> rows = httpListCaptor.getValue();
        assertEquals(1, rows.size());
        assertEquals("默认配置", rows.getFirst().getName());
    }

    @Test
    void createEnvironment_persistsVariablesPlaintext() {
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", null)).thenReturn(false);

        service.createEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, fullReq());

        verify(variableMapper).insertBatch(varListCaptor.capture());
        ApiEnvironmentVariable password = varListCaptor.getValue().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow();
        assertEquals("123456", password.getValue());
        assertEquals("https://staging.example.com", varListCaptor.getValue().stream()
                .filter(v -> "BASE_URL".equals(v.getName())).findFirst().orElseThrow().getValue());
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
    void updateEnvironment_promoteToDefault_clearsOthersAndReplacesChildren() {
        stubExistingEnv();
        ApiEnvironment existing = environmentMapper.selectById(ENV_ID);
        existing.setIsDefault(false);
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", ENV_ID)).thenReturn(false);
        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of());

        ApiEnvironmentSaveReqDTO req = fullReq();
        req.setIsDefault(true);
        service.updateEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        verify(environmentMapper).clearDefaultByProjectId(PROJECT_ID);
        verify(httpMapper).deleteByEnvironmentId(ENV_ID);
        verify(variableMapper).deleteByEnvironmentId(ENV_ID);
        verify(dataSourceMapper).deleteByEnvironmentId(ENV_ID);
        verify(processorMapper).deleteByEnvironmentId(ENV_ID);
    }

    @Test
    void updateEnvironment_blankVariableValueStoredAsIs() {
        stubExistingEnv();
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "测试环境", ENV_ID)).thenReturn(false);

        // 变量值留空提交：明文存储语义下直接落空值，无密文沿用逻辑
        ApiEnvironmentSaveReqDTO req = fullReq();
        req.getVariables().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow().setValue(null);
        service.updateEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        ArgumentCaptor<List<ApiEnvironmentVariable>> captor = ArgumentCaptor.captor();
        verify(variableMapper).insertBatch(captor.capture());
        assertNull(captor.getValue().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow().getValue());
    }

    @Test
    void deleteEnvironment_removesChildrenThenEnv_protectionStubPasses() {
        stubExistingEnv();

        service.deleteEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        verify(httpMapper).deleteByEnvironmentId(ENV_ID);
        verify(dataSourceMapper).deleteByEnvironmentId(ENV_ID);
        verify(environmentMapper).deleteById(ENV_ID);
    }

    // ==================== 设默认 / 排序 ====================

    @Test
    void setDefaultEnvironment_clearsAllThenMarksTarget() {
        stubExistingEnv();

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
        stubExistingEnv();

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
        stubExistingEnv();
        ApiEnvironmentVariable password = new ApiEnvironmentVariable();
        password.setId(UUID.randomUUID());
        password.setName("TEST_PASSWORD");
        password.setValue("123456");
        ApiEnvironmentVariable plain = new ApiEnvironmentVariable();
        plain.setId(UUID.randomUUID());
        plain.setName("BASE_URL");
        plain.setValue("https://staging.example.com");
        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(password, plain));
        when(httpMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of());
        when(dataSourceMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of());
        when(processorMapper.listByEnvironmentIdAndType(ENV_ID, null)).thenReturn(List.of());

        ApiEnvironmentDetailRespDTO detail = service.getEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID);

        ApiEnvironmentDetailRespDTO.Variable pwd = detail.getVariables().stream()
                .filter(v -> "TEST_PASSWORD".equals(v.getName())).findFirst().orElseThrow();
        assertEquals("123456", pwd.getValue());
        assertTrue(pwd.getHasValue());
        ApiEnvironmentDetailRespDTO.Variable textVar = detail.getVariables().stream()
                .filter(v -> "BASE_URL".equals(v.getName())).findFirst().orElseThrow();
        assertEquals("https://staging.example.com", textVar.getValue());
    }

    @Test
    void copyEnvironment_copiesVariablesWithValuesNoDataSources() {
        stubExistingEnv();
        ApiEnvironmentHttp http = new ApiEnvironmentHttp();
        http.setId(UUID.randomUUID());
        http.setEnvironmentId(ENV_ID);
        http.setName("内部 API");
        http.setRefName("http_1");
        http.setBaseUrl("https://staging.example.com");
        when(httpMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(http));
        ApiEnvironmentVariable token = new ApiEnvironmentVariable();
        token.setId(UUID.randomUUID());
        token.setName("TOKEN");
        token.setValue("abc123");
        ApiEnvironmentVariable plain = new ApiEnvironmentVariable();
        plain.setId(UUID.randomUUID());
        plain.setName("BASE_URL");
        plain.setValue("https://x");
        when(variableMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(token, plain));
        when(processorMapper.listByEnvironmentIdAndType(ENV_ID, null)).thenReturn(List.of());
        ApiDataSource ds = new ApiDataSource();
        ds.setId(UUID.randomUUID());
        ds.setEnvironmentId(ENV_ID);
        when(dataSourceMapper.listByEnvironmentId(ENV_ID)).thenReturn(List.of(ds));
        when(environmentMapper.listByProject(PROJECT_ID, null)).thenReturn(List.of());
        when(environmentMapper.existsByProjectIdAndName(PROJECT_ID, "预发环境（副本）", null)).thenReturn(false);

        ApiEnvironmentCopyReqDTO req = new ApiEnvironmentCopyReqDTO();
        req.setName("预发环境（副本）");
        service.copyEnvironment(PROJECT_ID, WORKSPACE_ID, USER_ID, ENV_ID, req);

        // 数据源不复制（详细设计 3.1.11）
        verify(dataSourceMapper, never()).insertBatch(any());
        // 变量随副本落库且保留取值（明文存储，无脱敏排除逻辑）
        verify(variableMapper).insertBatch(varListCaptor.capture());
        assertEquals(2, varListCaptor.getValue().size());
        ApiEnvironmentVariable copiedToken = varListCaptor.getValue().stream()
                .filter(v -> "TOKEN".equals(v.getName())).findFirst().orElseThrow();
        assertEquals("abc123", copiedToken.getValue());
    }
}
