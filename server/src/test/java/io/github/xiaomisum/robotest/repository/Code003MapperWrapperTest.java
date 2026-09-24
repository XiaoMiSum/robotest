package io.github.xiaomisum.robotest.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.model.entity.ai.AiConfig;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.ai.AiConfigMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CODE-003：复杂/固定 Wrapper 的 Mapper default 方法回归测试。 */
class Code003MapperWrapperTest {

    private static final UUID ID = UUID.randomUUID();

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        initTableInfo(configuration, ApiEnvironment.class);
        initTableInfo(configuration, ApiExecutionRecord.class);
        initTableInfo(configuration, AiConfig.class);
        initTableInfo(configuration, ApiScene.class);
        initTableInfo(configuration, ApiSwaggerUrl.class);
        initTableInfo(configuration, ApiScheduledTask.class);
    }

    @Test
    void environmentDefaultLookupBuildsProjectScopedQuery() {
        ApiEnvironmentMapper mapper = mock(ApiEnvironmentMapper.class, CALLS_REAL_METHODS);
        ApiEnvironment expected = new ApiEnvironment();
        when(mapper.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(expected);
        clearInvocations(mapper);

        assertSame(expected, mapper.findDefaultByProjectId(ID));

        ArgumentCaptor<Wrapper<ApiEnvironment>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("project_id"));
        assertTrue(sql.contains("is_default"));
    }

    @Test
    void executionRecordLookupBuildsOrderedSceneQuery() {
        ApiExecutionRecordMapper mapper = mock(ApiExecutionRecordMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of());
        clearInvocations(mapper);

        assertTrue(mapper.listLatestBySceneIds(List.of(ID)).isEmpty());
        assertTrue(mapper.listLatestBySceneIds(List.of()).isEmpty());

        ArgumentCaptor<Wrapper<ApiExecutionRecord>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("scene_id"));
        assertTrue(sql.contains("executed_at"));
    }

    @Test
    void aiConfigUpdateBuildsExplicitNullableColumnUpdate() {
        AiConfigMapper mapper = mock(AiConfigMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(LambdaUpdateWrapperX.class))).thenReturn(1);
        clearInvocations(mapper);

        assertEquals(1, mapper.updateConfig(ID, true, null, null, null, null, null, null, "{}", "{}"));

        ArgumentCaptor<Wrapper<AiConfig>> captor = wrapperCaptor();
        verify(mapper).update(isNull(), captor.capture());
        String sql = ((LambdaUpdateWrapperX<?>) captor.getValue()).getSqlSet();
        assertTrue(sql.contains("embedding_provider"));
        assertTrue(sql.contains("settings"));
    }

    @Test
    void sceneUpdateBuildsVersionGuard() {
        ApiSceneMapper mapper = mock(ApiSceneMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(any(ApiScene.class), any(LambdaUpdateWrapperX.class))).thenReturn(1);
        clearInvocations(mapper);
        ApiScene carrier = new ApiScene();
        carrier.setId(ID);
        carrier.setChangeVersion(4);

        assertEquals(1, mapper.updateByIdAndChangeVersion(ID, 3, carrier));

        ArgumentCaptor<Wrapper<ApiScene>> captor = wrapperCaptor();
        verify(mapper).update(eq(carrier), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("change_version"));
    }

    @Test
    void swaggerUpdateBuildsOnlySubmittedColumns() {
        ApiSwaggerUrlMapper mapper = mock(ApiSwaggerUrlMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(LambdaUpdateWrapperX.class))).thenReturn(1);
        clearInvocations(mapper);

        assertEquals(1, mapper.updateFieldsById(ID, "name", "https://example.com", "openapi"));

        ArgumentCaptor<Wrapper<ApiSwaggerUrl>> captor = wrapperCaptor();
        verify(mapper).update(isNull(), captor.capture());
        String sql = ((LambdaUpdateWrapperX<?>) captor.getValue()).getSqlSet();
        assertTrue(sql.contains("name"));
        assertTrue(sql.contains("url"));
        assertTrue(sql.contains("format"));
    }

    @Test
    void scheduledTaskPageBuildsProjectScopedQuery() {
        ApiScheduledTaskMapper mapper = mock(ApiScheduledTaskMapper.class, CALLS_REAL_METHODS);
        doReturn(new PageResult<ApiScheduledTask>(List.of(), 0L))
                .when(mapper).selectPage(any(PageParam.class), any(LambdaQueryWrapperX.class));
        clearInvocations(mapper);

        mapper.selectPageByProject(new PageParam(), ID, "scene_execute");

        ArgumentCaptor<Wrapper<ApiScheduledTask>> captor = wrapperCaptor();
        verify(mapper).selectPage(any(PageParam.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("project_id"));
        assertTrue(sql.contains("task_type"));
        assertTrue(sql.contains("created_at"));
    }

    @Test
    void scheduledTaskUpdatesKeepExplicitColumnSemantics() {
        ApiScheduledTaskMapper mapper = mock(ApiScheduledTaskMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(LambdaUpdateWrapperX.class))).thenReturn(1);
        clearInvocations(mapper);

        assertEquals(1, mapper.updateEditableFields(ID, "import_swagger", "name", null,
                null, null, null, null, null, "0 * * * *"));
        assertEquals(1, mapper.updateEnabled(ID, false));

        ArgumentCaptor<Wrapper<ApiScheduledTask>> updateCaptor = wrapperCaptor();
        verify(mapper, org.mockito.Mockito.times(2)).update(isNull(), updateCaptor.capture());
        String updateSql = ((LambdaUpdateWrapperX<?>) updateCaptor.getAllValues().get(0)).getSqlSet();
        assertTrue(updateSql.contains("task_type"));
        assertTrue(updateSql.contains("openapi_url"));
        String enabledSql = ((LambdaUpdateWrapperX<?>) updateCaptor.getAllValues().get(1)).getSqlSet();
        assertTrue(enabledSql.contains("enabled"));
    }

    private static void initTableInfo(MybatisConfiguration configuration, Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, entityClass.getName()), entityClass);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }
}
