package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentListItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import org.junit.jupiter.api.BeforeEach;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonComponentServiceImplTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    private CommonComponentMapper componentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private CommonComponentServiceImpl service;

    @BeforeEach
    void setUp() {
        // 单测环境无 MyBatis ID 自动填充，模拟插入回填主键
        lenient().when(componentMapper.insert(any(CommonComponent.class))).thenAnswer(inv -> {
            CommonComponent entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return 1;
        });
    }

    @Test
    void create_projectScope_insertsWithProjectOwnership() {
        CommonComponentSaveReqDTO reqDTO = reqDTO();
        when(componentMapper.existsByScopeAndTypeAndName("project", WORKSPACE_ID, PROJECT_ID,
                "preprocessor", "签名头", null)).thenReturn(false);

        CommonComponentIdRespDTO respDTO = service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).insert(captor.capture());
        assertEquals(PROJECT_ID, captor.getValue().getProjectId());
        assertNull(captor.getValue().getWorkspaceId());
        assertEquals("project", captor.getValue().getScope());
        assertEquals(Boolean.TRUE, captor.getValue().getEnabled());
        assertEquals(5, captor.getValue().getSortOrder());
        assertEquals(respDTO.getId(), captor.getValue().getId().toString());
    }

    @Test
    void create_withoutSortOrder_defaultsToZero() {
        CommonComponentSaveReqDTO reqDTO = reqDTO();
        reqDTO.setSortOrder(null);
        when(componentMapper.existsByScopeAndTypeAndName(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSortOrder());
    }

    @Test
    void create_duplicateName_throwsNameExists() {
        when(componentMapper.existsByScopeAndTypeAndName(any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO()));
        assertEquals(ErrorCodeConstants.API_COMMON_COMPONENT_NAME_EXISTS.code(), exception.getCode());
    }

    @Test
    void create_workspaceScope_allowedAndStampsWorkspace() {
        CommonComponentSaveReqDTO reqDTO = reqDTO();
        reqDTO.setScope("workspace");
        lenient().when(componentMapper.existsByScopeAndTypeAndName(any(), any(), any(),
                any(), any(), any())).thenReturn(false);

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).insert(captor.capture());
        assertEquals(WORKSPACE_ID, captor.getValue().getWorkspaceId());
        assertNull(captor.getValue().getProjectId());
    }

    @Test
    void update_nameChanged_updatesSuccessfully() {
        CommonComponent existing = entity("project", "preprocessor", "旧名");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);
        when(componentMapper.existsByScopeAndTypeAndName("project", WORKSPACE_ID, PROJECT_ID,
                "preprocessor", "新名", existing.getId())).thenReturn(false);

        CommonComponentSaveReqDTO reqDTO = reqDTO();
        reqDTO.setName("新名");
        reqDTO.setSortOrder(9);

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId(), reqDTO);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).updateById(captor.capture());
        assertEquals("新名", captor.getValue().getName());
        assertEquals(9, captor.getValue().getSortOrder());
    }

    @Test
    void update_scopeChange_allowedAndStampsScope() {
        CommonComponent existing = entity("project", "preprocessor", "旧名");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);
        when(componentMapper.existsByScopeAndTypeAndName(any(), any(), any(), any(), any(), any()))
                .thenReturn(false);

        CommonComponentSaveReqDTO reqDTO = reqDTO();
        reqDTO.setScope("global");

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId(), reqDTO);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).updateById(captor.capture());
        assertEquals("global", captor.getValue().getScope());
        assertNull(captor.getValue().getProjectId());
        assertNull(captor.getValue().getWorkspaceId());
    }

    @Test
    void update_typeChange_rejected() {
        CommonComponent existing = entity("project", "preprocessor", "旧名");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);

        CommonComponentSaveReqDTO reqDTO = reqDTO();
        reqDTO.setType("validator");

        assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId(), reqDTO));
    }

    @Test
    void toggle_globalScope_togglesEnabled() {
        CommonComponent existing = entity("global", "validator", "断言器");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);

        service.toggle(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId(), false);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).updateById(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getEnabled());
    }

    @Test
    void toggle_projectScope_togglesEnabled() {
        CommonComponent existing = entity("project", "preprocessor", "处理器");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);

        service.toggle(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId(), false);

        ArgumentCaptor<CommonComponent> captor = ArgumentCaptor.forClass(CommonComponent.class);
        verify(componentMapper).updateById(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getEnabled());
    }

    @Test
    void delete_visibleRecord_deletesById() {
        CommonComponent existing = entity("project", "postprocessor", "清理器");
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, existing.getId()))
                .thenReturn(existing);

        service.delete(WORKSPACE_ID, PROJECT_ID, USER_ID, existing.getId());

        verify(componentMapper).deleteById(existing.getId());
    }

    @Test
    void batchToggle_projectScope_togglesAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        CommonComponent comp1 = entity("project", "preprocessor", "组件1");
        comp1.setId(id1);
        CommonComponent comp2 = entity("project", "preprocessor", "组件2");
        comp2.setId(id2);

        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, id1)).thenReturn(comp1);
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, id2)).thenReturn(comp2);

        service.batchToggle(WORKSPACE_ID, PROJECT_ID, USER_ID, List.of(id1, id2), false);

        verify(componentMapper, org.mockito.Mockito.times(2)).updateById(any(CommonComponent.class));
    }

    @Test
    void batchDelete_projectScope_deletesAll() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        CommonComponent comp1 = entity("project", "preprocessor", "组件1");
        comp1.setId(id1);
        CommonComponent comp2 = entity("project", "preprocessor", "组件2");
        comp2.setId(id2);

        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, id1)).thenReturn(comp1);
        when(componentMapper.findVisibleById(PROJECT_ID, WORKSPACE_ID, id2)).thenReturn(comp2);

        service.batchDelete(WORKSPACE_ID, PROJECT_ID, USER_ID, List.of(id1, id2));

        verify(componentMapper).deleteById(id1);
        verify(componentMapper).deleteById(id2);
    }

    @Test
    void fetchList_withFilters_returnsPageResult() {
        PageParam pageParam = new PageParam() {{
            setPageNo(1);
            setPageSize(10);
        }};
        CommonComponent comp = entity("project", "preprocessor", "测试组件");
        PageResult<CommonComponent> pageResult = new PageResult<>(List.of(comp), 1L);
        when(componentMapper.selectPageVisible(PROJECT_ID, WORKSPACE_ID, "preprocessor",
                true, "project", "测试", pageParam)).thenReturn(pageResult);

        PageResult<CommonComponentListItemRespDTO> result = service.fetchList(
                WORKSPACE_ID, PROJECT_ID, USER_ID, pageParam, "preprocessor", true, "project", "测试");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("测试组件", result.getList().get(0).getName());
        assertEquals(3, result.getList().get(0).getSortOrder());
    }

    private static CommonComponentSaveReqDTO reqDTO() {
        CommonComponentSaveReqDTO reqDTO = new CommonComponentSaveReqDTO();
        reqDTO.setType("preprocessor");
        reqDTO.setName("签名头");
        reqDTO.setDescription("加签前置处理器");
        reqDTO.setScope("project");
        reqDTO.setConfig(Map.of("algorithm", "md5"));
        reqDTO.setSortOrder(5);
        return reqDTO;
    }

    private static CommonComponent entity(String scope, String type, String name) {
        CommonComponent entity = new CommonComponent();
        entity.setId(UUID.randomUUID());
        entity.setScope(scope);
        entity.setType(type);
        entity.setName(name);
        entity.setEnabled(true);
        entity.setSortOrder(3);
        return entity;
    }
}
