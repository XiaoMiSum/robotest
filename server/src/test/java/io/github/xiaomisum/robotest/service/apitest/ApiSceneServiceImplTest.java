package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneInterfaceAssociateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneSettingsReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiSceneStep;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneStepVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScenarioVariableMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 场景管理核心路径：乐观锁/设置不递增版本/重复关联/重排归属（测试场景详细设计 3.1-3.5） */
@ExtendWith(MockitoExtension.class)
class ApiSceneServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SCENE_ID = UUID.randomUUID();
    private static final int CODE_7205 = 1000017303;
    private static final int CODE_7208 = 1000017306;

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiSceneStepMapper stepMapper;
    @Mock
    private ApiSceneStepVariableMapper stepVariableMapper;
    @Mock
    private ApiScenarioVariableMapper scenarioVariableMapper;
    @Mock
    private ApiSceneInterfaceMapper sceneInterfaceMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private ApiInterfaceStepMapper interfaceStepMapper;
    @Mock
    private ApiInterfaceVariableMapper interfaceVariableMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CommonComponentMapper componentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private ApiSceneServiceImpl service;

    private ApiScene existingScene() {
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("登录链路");
        scene.setChangeVersion(3);
        return scene;
    }

    private void stubScene() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(existingScene());
    }

    // ========== 创建 ==========

    @Test
    void createDefaultsFailureRuleAndWritesFirstHistoryVersion() {
        when(changeHistoryMapper.selectMaxVersion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(scenarioVariableMapper.listBySceneId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, new ApiSceneCreateReqDTO());

        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(sceneCaptor.capture());
        UUID createdId = sceneCaptor.getValue().getId();
        org.junit.jupiter.api.Assertions.assertNotNull(createdId);
        assertEquals("all", sceneCaptor.getValue().getFailureRule());
        assertEquals(1, sceneCaptor.getValue().getChangeVersion());

        ArgumentCaptor<ApiChangeHistory> historyCaptor = ArgumentCaptor.forClass(ApiChangeHistory.class);
        verify(changeHistoryMapper).insert(historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().getVersion());
        assertEquals("create", historyCaptor.getValue().getChangeType());
    }

    @Test
    void createRejectsUnknownFailureRule() {
        ApiSceneCreateReqDTO reqDTO = new ApiSceneCreateReqDTO();
        reqDTO.setFailureRule("sometimes");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO));
        assertEquals(1000017304, ex.getCode());
    }

    // ========== 更新（乐观锁） ==========

    @Test
    void updateThrowsVersionConflictWhenNoRowMatched() {
        stubScene();
        ApiSceneUpdateReqDTO reqDTO = new ApiSceneUpdateReqDTO();
        reqDTO.setName("新名字");
        reqDTO.setChangeVersion(2);
        when(sceneMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO));
        assertEquals(CODE_7205, ex.getCode().longValue());
    }

    @Test
    void updateSucceedsAndIncrementsVersion() {
        stubScene();
        ApiSceneUpdateReqDTO reqDTO = new ApiSceneUpdateReqDTO();
        reqDTO.setName("新名字");
        reqDTO.setChangeVersion(3);
        when(sceneMapper.update(any(), any())).thenReturn(1);
        when(changeHistoryMapper.selectMaxVersion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);
        verify(changeHistoryMapper).insert(any(ApiChangeHistory.class));
    }

    // ========== 设置（不递增 changeVersion） ==========

    @Test
    void updateSettingsKeepsChangeVersionUntouched() {
        stubScene();
        ApiSceneSettingsReqDTO reqDTO = new ApiSceneSettingsReqDTO();
        reqDTO.setFailureRule("continue");
        when(changeHistoryMapper.selectMaxVersion("scene", SCENE_ID)).thenReturn(4);

        service.updateSettings(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        assertNull(captor.getValue().getChangeVersion());
        assertEquals("continue", captor.getValue().getFailureRule());
    }

    // ========== 关联接口 ==========

    @Test
    void associateRejectsDuplicateAssociation() {
        stubScene();
        UUID interfaceId = UUID.randomUUID();
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(interfaceId);
        apiInterface.setProjectId(PROJECT_ID);
        when(interfaceMapper.selectById(interfaceId)).thenReturn(apiInterface);
        when(sceneInterfaceMapper.selectBySceneAndInterface(SCENE_ID, interfaceId))
                .thenReturn(new ApiSceneInterface());

        ApiSceneInterfaceAssociateReqDTO reqDTO = new ApiSceneInterfaceAssociateReqDTO();
        reqDTO.setSyncMode("link");
        reqDTO.setInterfaceIds(java.util.List.of(interfaceId));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.associateInterfaces(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO));
        assertEquals(CODE_7208, ex.getCode().longValue());
    }

    @Test
    void associateInsertsWithNormalizedMode() {
        stubScene();
        UUID interfaceId = UUID.randomUUID();
        ApiInterface apiInterface = new ApiInterface();
        apiInterface.setId(interfaceId);
        apiInterface.setProjectId(PROJECT_ID);
        when(interfaceMapper.selectById(interfaceId)).thenReturn(apiInterface);
        when(sceneInterfaceMapper.selectBySceneAndInterface(SCENE_ID, interfaceId)).thenReturn(null);

        ApiSceneInterfaceAssociateReqDTO reqDTO = new ApiSceneInterfaceAssociateReqDTO();
        reqDTO.setSyncMode("copy");
        reqDTO.setInterfaceIds(java.util.List.of(interfaceId));

        service.associateInterfaces(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        ArgumentCaptor<ApiSceneInterface> captor = ArgumentCaptor.forClass(ApiSceneInterface.class);
        verify(sceneInterfaceMapper).insert(captor.capture());
        assertEquals("copy", captor.getValue().getSyncMode());
    }

    // ========== 步骤重排 ==========

    @Test
    void reorderRejectsForeignStepIds() {
        UUID owned = UUID.randomUUID();
        ApiSceneStep step = new ApiSceneStep();
        step.setId(owned);
        step.setSceneId(SCENE_ID);
        when(stepMapper.listBySceneId(SCENE_ID)).thenReturn(List.of(step));

        ApiSceneStepReorderReqDTO reqDTO = new ApiSceneStepReorderReqDTO();
        reqDTO.setStepIds(List.of(owned, UUID.randomUUID()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.reorderSteps(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO));
        assertEquals(1000017305, ex.getCode());
        verify(stepMapper, org.mockito.Mockito.never()).reorder(any(), any());
    }

    @Test
    void reorderAppliesIncomingOrderForOwnedSteps() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ApiSceneStep stepA = new ApiSceneStep();
        stepA.setId(first);
        stepA.setSceneId(SCENE_ID);
        ApiSceneStep stepB = new ApiSceneStep();
        stepB.setId(second);
        stepB.setSceneId(SCENE_ID);
        when(stepMapper.listBySceneId(SCENE_ID)).thenReturn(List.of(stepA, stepB));

        ApiSceneStepReorderReqDTO reqDTO = new ApiSceneStepReorderReqDTO();
        reqDTO.setStepIds(List.of(second, first));
        service.reorderSteps(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        verify(stepMapper).reorder(SCENE_ID, List.of(second, first));
    }

    // ========== 全局资产引入 ==========

    @Test
    void importAssetsSceneProcessor_insertsOrderedByComponentSortOrder() {
        stubScene();
        UUID lowId = UUID.randomUUID();
        UUID highId = UUID.randomUUID();
        CommonComponent low = component("请求头签名", 2);
        low.setId(lowId);
        CommonComponent high = component("Token 预置", 0);
        high.setId(highId);
        when(componentMapper.selectById(lowId)).thenReturn(low);
        when(componentMapper.selectById(highId)).thenReturn(high);
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(existingScene());

        ApiSceneAssetsImportReqDTO reqDTO = new ApiSceneAssetsImportReqDTO();
        reqDTO.setTarget("scene_processor");
        reqDTO.setAssetIds(List.of(lowId, highId));

        service.importAssets(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        List<Map<String, Object>> processors = captor.getValue().getProcessors();
        assertEquals(2, processors.size());
        assertEquals("Token 预置", processors.get(0).get("name"));
        assertEquals("请求头签名", processors.get(1).get("name"));
    }

    private static CommonComponent component(String name, int sortOrder) {
        CommonComponent c = new CommonComponent();
        c.setName(name);
        c.setSortOrder(sortOrder);
        c.setEnabled(true);
        c.setConfig("{\"handlerType\":\"http\"}");
        return c;
    }
}
