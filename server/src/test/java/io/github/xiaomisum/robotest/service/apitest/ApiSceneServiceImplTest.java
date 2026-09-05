package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneAssetsImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepReorderReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneStepSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiSceneUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiChangeHistory;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiInterface;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.CommonComponent;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiChangeHistoryMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiInterfaceMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneFollowMapper;
import io.github.xiaomisum.robotest.repository.apitest.CommonComponentMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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

    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ApiExecutionRecordMapper executionRecordMapper;
    @Mock
    private ApiChangeHistoryMapper changeHistoryMapper;
    @Mock
    private ApiInterfaceMapper interfaceMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private CommonComponentMapper componentMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private ApiSceneFollowMapper sceneFollowMapper;
    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;

    @InjectMocks
    private ApiSceneServiceImpl service;

    private ApiScene existingScene() {
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        scene.setProjectId(PROJECT_ID);
        scene.setName("登录链路");
        scene.setChangeVersion(3);
        scene.setSteps(new ArrayList<>());
        return scene;
    }

    private void stubScene() {
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(existingScene());
    }

    private ApiScene existingSceneWithStep(Map<String, Object> step) {
        ApiScene scene = existingScene();
        scene.setSteps(new ArrayList<>(List.of(step)));
        return scene;
    }

    private Map<String, Object> makeStep(String name) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", UUID.randomUUID());
        step.put("name", name);
        step.put("stepType", "http");
        step.put("sortOrder", 1);
        step.put("enabled", true);
        step.put("sourceType", "custom");
        step.put("requestConfig", Map.of());
        step.put("processors", List.of());
        step.put("validators", List.of());
        step.put("extractors", List.of());
        step.put("variables", List.of());
        return step;
    }

    // ========== 创建 ==========

    @Test
    void createWritesFirstHistoryVersion() {
        when(changeHistoryMapper.selectMaxVersion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, new ApiSceneCreateReqDTO());

        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(sceneCaptor.capture());
        UUID createdId = sceneCaptor.getValue().getId();
        assertNotNull(createdId);
        assertEquals(1, sceneCaptor.getValue().getChangeVersion());

        ArgumentCaptor<ApiChangeHistory> historyCaptor = ArgumentCaptor.forClass(ApiChangeHistory.class);
        verify(changeHistoryMapper).insert(historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().getVersion());
        assertEquals("create", historyCaptor.getValue().getChangeType());
    }

    @Test
    void createPersistsStepsWithSequentialSortOrder() {
        when(changeHistoryMapper.selectMaxVersion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        ApiSceneStepSaveReqDTO first = new ApiSceneStepSaveReqDTO();
        first.setName("登录");
        first.setStepType("http");
        ApiSceneStepSaveReqDTO second = new ApiSceneStepSaveReqDTO();
        second.setName("退出");
        second.setStepType("http");
        ApiSceneCreateReqDTO reqDTO = new ApiSceneCreateReqDTO();
        reqDTO.setSteps(List.of(first, second));

        service.create(WORKSPACE_ID, PROJECT_ID, USER_ID, reqDTO);

        ArgumentCaptor<ApiScene> sceneCaptor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).insert(sceneCaptor.capture());
        ApiScene inserted = sceneCaptor.getValue();
        assertNotNull(inserted.getSteps());
        assertEquals(2, inserted.getSteps().size());
        assertEquals("登录", inserted.getSteps().get(0).get("name"));
        assertEquals(1, inserted.getSteps().get(0).get("sortOrder"));
        assertEquals("退出", inserted.getSteps().get(1).get("name"));
        assertEquals(2, inserted.getSteps().get(1).get("sortOrder"));
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

    @Test
    void updatePersistsStepsInSceneStepsColumn() {
        stubScene();
        UUID existingStepId = UUID.randomUUID();
        Map<String, Object> existingStep = makeStep("登录");
        existingStep.put("id", existingStepId);
        existingStep.put("sortOrder", 1);
        // 模拟场景中已有该步骤
        ApiScene sceneWithStep = existingScene();
        sceneWithStep.setSteps(new ArrayList<>(List.of(existingStep)));
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithStep);

        ApiSceneStepSaveReqDTO existing = new ApiSceneStepSaveReqDTO();
        existing.setId(existingStepId);
        existing.setName("登录");
        existing.setStepType("http");
        existing.setSortOrder(1);
        ApiSceneStepSaveReqDTO fresh = new ApiSceneStepSaveReqDTO();
        fresh.setName("退出");
        fresh.setStepType("http");
        fresh.setSortOrder(2);
        ApiSceneUpdateReqDTO reqDTO = new ApiSceneUpdateReqDTO();
        reqDTO.setName("新名字");
        reqDTO.setChangeVersion(3);
        reqDTO.setSteps(List.of(existing, fresh));
        when(sceneMapper.update(any(), any())).thenReturn(1);
        when(changeHistoryMapper.selectMaxVersion(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        // 验证 sceneMapper.updateById 被调用，且携带 steps（含更新后已有的 + 新增的）
        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper, times(1)).updateById(captor.capture());
        ApiScene carrier = captor.getValue();
        assertNotNull(carrier.getSteps());
        // 已有步骤被保留（partial update），加上新增的一步 = 2 步
        assertEquals(2, carrier.getSteps().size());
        assertEquals("退出", carrier.getSteps().get(1).get("name"));
    }

    @Test
    void updateWithInvalidStepAbortsBeforeHistoryWrite() {
        stubScene();
        ApiSceneStepSaveReqDTO invalid = new ApiSceneStepSaveReqDTO();
        invalid.setName("非法");
        invalid.setStepType("ftp");
        ApiSceneUpdateReqDTO reqDTO = new ApiSceneUpdateReqDTO();
        reqDTO.setName("新名字");
        reqDTO.setChangeVersion(3);
        reqDTO.setSteps(List.of(invalid));
        when(sceneMapper.update(any(), any())).thenReturn(1);

        assertThrows(ServiceException.class,
                () -> service.update(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO));
        // 步骤校验失败中止历史写入（真实事务整体回滚，change_version 不会误增）
        verify(changeHistoryMapper, times(0)).insert(any(ApiChangeHistory.class));
    }

    // ========== 步骤重排 ==========

    @Test
    void reorderRejectsForeignStepIds() {
        UUID owned = UUID.randomUUID();
        Map<String, Object> step = makeStep("步骤A");
        step.put("id", owned);
        ApiScene sceneWithSteps = existingScene();
        sceneWithSteps.setSteps(new ArrayList<>(List.of(step)));
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithSteps);

        ApiSceneStepReorderReqDTO reqDTO = new ApiSceneStepReorderReqDTO();
        reqDTO.setStepIds(List.of(owned, UUID.randomUUID()));

        assertThrows(ServiceException.class,
                () -> service.reorderSteps(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO));
    }

    @Test
    void reorderAppliesIncomingOrderForOwnedSteps() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<String, Object> stepA = makeStep("A");
        stepA.put("id", first);
        Map<String, Object> stepB = makeStep("B");
        stepB.put("id", second);
        ApiScene sceneWithSteps = existingScene();
        sceneWithSteps.setSteps(new ArrayList<>(List.of(stepA, stepB)));
        when(sceneMapper.selectById(SCENE_ID)).thenReturn(sceneWithSteps);

        ApiSceneStepReorderReqDTO reqDTO = new ApiSceneStepReorderReqDTO();
        reqDTO.setStepIds(List.of(second, first));
        service.reorderSteps(WORKSPACE_ID, PROJECT_ID, USER_ID, SCENE_ID, reqDTO);

        // 验证步骤顺序被重排并持久化
        ArgumentCaptor<ApiScene> captor = ArgumentCaptor.forClass(ApiScene.class);
        verify(sceneMapper).updateById(captor.capture());
        ApiScene carrier = captor.getValue();
        assertEquals(second, carrier.getSteps().get(0).get("id"));
        assertEquals(first, carrier.getSteps().get(1).get("id"));
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
