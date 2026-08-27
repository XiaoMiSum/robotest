package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.mock.MockAccessProperties;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockBatchToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockAddressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockBatchToggleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockMoveRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiMockDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiMockServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MOCK_ID = UUID.randomUUID();

    @Mock
    private ApiMockDefinitionMapper mockMapper;
    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private Environment environment;

    @InjectMocks
    private ApiMockServiceImpl service;

    @BeforeEach
    void injectRealDependencies() {
        // ObjectMapper/配置对象无外部状态，直接以真实实例注入
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        MockAccessProperties properties = new MockAccessProperties();
        properties.setBaseUrl("http://localhost:18080/");
        ReflectionTestUtils.setField(service, "mockAccessProperties", properties);
    }

    private ApiMockDefinition existing(UUID id, String method, String path, boolean enabled, int priority) {
        ApiMockDefinition definition = new ApiMockDefinition();
        definition.setId(id);
        definition.setProjectId(PROJECT_ID);
        definition.setMethod(method);
        definition.setPath(path);
        definition.setEnabled(enabled);
        definition.setPriority(priority);
        return definition;
    }

    private ApiMockSaveReqDTO saveRequest(String method, String path, Boolean enabled) {
        ApiMockSaveReqDTO reqDTO = new ApiMockSaveReqDTO();
        reqDTO.setName("mock-1");
        reqDTO.setMethod(method);
        reqDTO.setPath(path);
        reqDTO.setEnabled(enabled);
        reqDTO.setResponseBodyType("json");
        reqDTO.setResponseBody("{}");
        return reqDTO;
    }

    @Test
    void fetchPageMapsItemsAndKeepsTotal() {
        when(mockMapper.selectPage(eq(PROJECT_ID), isNull(), isNull(), isNull(), any(PageParam.class)))
                .thenReturn(new PageResult<>(List.of(existing(MOCK_ID, "GET", "/api/a", true, 1)), 7L));

        PageResult<ApiMockItemRespDTO> page = service.fetchPage(WORKSPACE_ID, PROJECT_ID, USER_ID,
                null, null, null, new PageParam());

        assertEquals(7L, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals(MOCK_ID, page.getList().get(0).getId());
        verify(projectAccessGuard).requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);
    }

    @Test
    void createDefaultsPriorityToGroupMaxPlusOne() {
        ApiMockSaveReqDTO reqDTO = saveRequest("GET", "/api/new", false);
        when(mockMapper.selectMaxPriority(PROJECT_ID, "GET", "/api/new")).thenReturn(3);

        ApiMockIdRespDTO created = service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<ApiMockDefinition> captor = ArgumentCaptor.forClass(ApiMockDefinition.class);
        verify(mockMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getPriority());
        assertFalse(captor.getValue().getEnabled());
    }

    @Test
    void createRejectsEnabledAddressConflict() {
        ApiMockSaveReqDTO reqDTO = saveRequest("GET", "/api/dup", true);
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/dup"))
                .thenReturn(List.of(existing(UUID.randomUUID(), "GET", "/api/dup", true, 1)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(ErrorCodeConstants.API_MOCK_ADDR_CONFLICT.code(), exception.getCode());
        verify(mockMapper, never()).insert(any(ApiMockDefinition.class));
    }

    @Test
    void crossProjectDetailReportedAsNotFound() {
        when(mockMapper.selectById(MOCK_ID)).thenReturn(existing(MOCK_ID, "GET", "/api/a", true, 1));
        UUID otherProject = UUID.randomUUID();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(WORKSPACE_ID, otherProject, USER_ID, MOCK_ID));
        assertEquals(ErrorCodeConstants.API_MOCK_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void toggleEnableChecksConflictAndUpdatesCarrierOnly() {
        when(mockMapper.selectById(MOCK_ID)).thenReturn(existing(MOCK_ID, "GET", "/api/a", false, 1));
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/a")).thenReturn(List.of());

        service.toggle(WORKSPACE_ID, PROJECT_ID, USER_ID, MOCK_ID, true);

        ArgumentCaptor<ApiMockDefinition> captor = ArgumentCaptor.forClass(ApiMockDefinition.class);
        verify(mockMapper).updateById(captor.capture());
        assertEquals(MOCK_ID, captor.getValue().getId());
        assertTrue(captor.getValue().getEnabled());
        // C9：载体仅含 id/enabled，不携带整行数据回写
        assertNull(captor.getValue().getMethod());
        assertNull(captor.getValue().getName());
    }

    @Test
    void batchToggleSkipsConflictingRules() {
        UUID conflictingId = UUID.randomUUID();
        ApiMockBatchToggleReqDTO reqDTO = new ApiMockBatchToggleReqDTO();
        reqDTO.setIds(List.of(MOCK_ID, conflictingId));
        reqDTO.setEnabled(true);
        when(mockMapper.selectById(MOCK_ID)).thenReturn(existing(MOCK_ID, "GET", "/api/a", false, 1));
        when(mockMapper.selectById(conflictingId))
                .thenReturn(existing(conflictingId, "GET", "/api/b", false, 1));
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/a")).thenReturn(List.of());
        // conflicting 规则组内已有启用规则 → 跳过
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/b"))
                .thenReturn(List.of(existing(UUID.randomUUID(), "GET", "/api/b", true, 2)));

        ApiMockBatchToggleRespDTO result = service.batchToggle(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        assertEquals(1, result.getUpdatedCount());
        verify(mockMapper, times(1)).updateById(any(ApiMockDefinition.class));
    }

    @Test
    void duplicateProducesDisabledCopyWithoutHitStats() {
        ApiMockDefinition source = existing(MOCK_ID, "POST", "/api/order", true, 5);
        source.setName("下单");
        source.setHitCount(99L);
        when(mockMapper.selectById(MOCK_ID)).thenReturn(source);
        when(mockMapper.selectMaxPriority(PROJECT_ID, "POST", "/api/order")).thenReturn(5);

        ApiMockIdRespDTO copy = service.duplicate(WORKSPACE_ID, PROJECT_ID, USER_ID, MOCK_ID);

        ArgumentCaptor<ApiMockDefinition> captor = ArgumentCaptor.forClass(ApiMockDefinition.class);
        verify(mockMapper).insert(captor.capture());
        assertEquals("下单 - 副本", captor.getValue().getName());
        assertFalse(captor.getValue().getEnabled());
        assertEquals(0L, captor.getValue().getHitCount());
        assertEquals(6, captor.getValue().getPriority());
    }

    @Test
    void moveUpSwapsPrioritiesWithinGroup() {
        UUID firstId = UUID.randomUUID();
        ApiMockDefinition first = existing(firstId, "GET", "/api/list", true, 10);
        ApiMockDefinition second = existing(MOCK_ID, "GET", "/api/list", true, 20);
        when(mockMapper.selectById(MOCK_ID)).thenReturn(second);
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/list")).thenReturn(List.of(first, second));

        ApiMockMoveRespDTO result = service.moveUp(WORKSPACE_ID, PROJECT_ID, USER_ID, MOCK_ID);

        assertTrue(Boolean.TRUE.equals(result.getSuccess()));
        ArgumentCaptor<ApiMockDefinition> captor = ArgumentCaptor.forClass(ApiMockDefinition.class);
        verify(mockMapper, times(2)).updateById(captor.capture());
        // 两条载体按交换顺序：自身降为邻居原优先级，邻居升为自身原优先级
        var updates = captor.getAllValues();
        assertEquals(MOCK_ID, updates.get(0).getId());
        assertEquals(10, updates.get(0).getPriority());
        assertEquals(firstId, updates.get(1).getId());
        assertEquals(20, updates.get(1).getPriority());
    }

    @Test
    void moveDownAtGroupEdgeReturnsFailure() {
        when(mockMapper.selectById(MOCK_ID)).thenReturn(existing(MOCK_ID, "GET", "/api/list", true, 20));
        when(mockMapper.selectGroup(PROJECT_ID, "GET", "/api/list")).thenReturn(List.of(
                existing(UUID.randomUUID(), "GET", "/api/list", true, 10),
                existing(MOCK_ID, "GET", "/api/list", true, 20)));

        ApiMockMoveRespDTO result = service.moveDown(WORKSPACE_ID, PROJECT_ID, USER_ID, MOCK_ID);

        assertFalse(Boolean.TRUE.equals(result.getSuccess()));
        verify(mockMapper, never()).updateById(any(ApiMockDefinition.class));
    }

    @Test
    void addressUsesConfiguredBaseUrlWithoutTrailingSlash() {
        when(mockMapper.selectById(MOCK_ID)).thenReturn(existing(MOCK_ID, "GET", "/api/users", true, 1));

        ApiMockAddressRespDTO address = service.getAddress(WORKSPACE_ID, PROJECT_ID, USER_ID, MOCK_ID);

        assertEquals("http://localhost:18080/api/users", address.getMockUrl());
        assertEquals("GET", address.getMethod());
    }

}
