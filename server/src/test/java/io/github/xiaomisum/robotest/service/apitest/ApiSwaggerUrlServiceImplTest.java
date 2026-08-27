package io.github.xiaomisum.robotest.service.apitest;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSwaggerUrlSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiSwaggerUrlItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSwaggerUrl;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSwaggerUrlMapper;
import io.github.xiaomisum.robotest.service.apitest.imports.ImportSourceFetcher;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.migoo.framework.common.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Swagger URL 配置管理（定时任务详细设计 3.1.9）：SSRF 校验、删除保护 */
@ExtendWith(MockitoExtension.class)
class ApiSwaggerUrlServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONFIG_ID = UUID.randomUUID();

    @Mock
    private ApiSwaggerUrlMapper swaggerUrlMapper;
    @Mock
    private ApiScheduledTaskMapper taskMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ImportSourceFetcher sourceFetcher;

    @InjectMocks
    private ApiSwaggerUrlServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // wrapper 更新（C9）需要 MyBatis-Plus 的 lambda 列缓存，纯单测环境下手动初始化；
        // UUID 主键列还需注册框架 UUIDTypeHandler，否则 TableInfo 构建失败
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class,
                xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), ApiSwaggerUrl.class);
    }

    @BeforeEach
    void injectFetcher() {
        ReflectionTestUtils.setField(service, "sourceFetcher", sourceFetcher);
    }

    @Test
    void createValidatesReachabilityThenInserts() {
        doReturn("{}").when(sourceFetcher).fetch(anyString());

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, req());

        ArgumentCaptor<ApiSwaggerUrl> captor = ArgumentCaptor.forClass(ApiSwaggerUrl.class);
        verify(swaggerUrlMapper).insert(captor.capture());
        assertEquals(PROJECT_ID, captor.getValue().getProjectId());
        assertEquals("生产 Swagger", captor.getValue().getName());
        assertEquals("openapi", captor.getValue().getFormat());
    }

    @Test
    void createRejectsUnreachableUrlWithoutInserting() {
        doThrow(new ServiceException(1000017012, "HTTP 500"))
                .when(sourceFetcher).fetch(anyString());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, req()));
        assertEquals(1000017012, ex.getCode().intValue());
        verifyNoInteractions(swaggerUrlMapper);
    }

    @Test
    void updateRejectsForeignConfig() {
        ApiSwaggerUrl foreign = new ApiSwaggerUrl();
        foreign.setId(CONFIG_ID);
        foreign.setProjectId(UUID.randomUUID());
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, CONFIG_ID, req()));
        assertEquals(1000017604, ex.getCode().intValue());
        verifyNoInteractions(sourceFetcher);
    }

    @Test
    void updateValidatesNewUrlBeforeWriting() {
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(config());
        doReturn("{}").when(sourceFetcher).fetch(anyString());

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, CONFIG_ID, req());

        // C9 部分更新：仅写名称/URL/格式三列，载体为 wrapper 而非整行
        verify(swaggerUrlMapper).update(any(), any());
    }

    @Test
    void deleteRejectsWhenBoundByImportTask() {
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(config());
        when(taskMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.delete(WORKSPACE_ID, PROJECT_ID, USER_ID, CONFIG_ID));
        assertEquals(1000017605, ex.getCode().intValue());
        verify(swaggerUrlMapper, never()).deleteById(any());
    }

    @Test
    void deleteRemovesUnboundConfig() {
        when(swaggerUrlMapper.selectById(CONFIG_ID)).thenReturn(config());
        when(taskMapper.selectCount(any())).thenReturn(0L);

        service.delete(WORKSPACE_ID, PROJECT_ID, USER_ID, CONFIG_ID);

        verify(swaggerUrlMapper).deleteById(CONFIG_ID);
    }

    @Test
    void listMapsFieldsIncludingLastImportState() {
        ApiSwaggerUrl config = config();
        config.setLastImportStatus("success");
        config.setLastImportAt(LocalDateTime.of(2026, 8, 25, 2, 0));
        when(swaggerUrlMapper.selectListByProject(PROJECT_ID, null)).thenReturn(List.of(config));

        List<ApiSwaggerUrlItemRespDTO> items = service.list(WORKSPACE_ID, PROJECT_ID, USER_ID, null);

        assertEquals(1, items.size());
        assertEquals(CONFIG_ID, items.get(0).getId());
        assertEquals("success", items.get(0).getLastImportStatus());
        assertEquals(LocalDateTime.of(2026, 8, 25, 2, 0), items.get(0).getLastImportAt());
    }

    private ApiSwaggerUrlSaveReqDTO req() {
        ApiSwaggerUrlSaveReqDTO reqDTO = new ApiSwaggerUrlSaveReqDTO();
        reqDTO.setName("生产 Swagger");
        reqDTO.setUrl("https://petstore.example.com/v2/swagger.json ");
        reqDTO.setFormat("openapi");
        return reqDTO;
    }

    private ApiSwaggerUrl config() {
        ApiSwaggerUrl config = new ApiSwaggerUrl();
        config.setId(CONFIG_ID);
        config.setProjectId(PROJECT_ID);
        config.setName("生产 Swagger");
        return config;
    }
}
