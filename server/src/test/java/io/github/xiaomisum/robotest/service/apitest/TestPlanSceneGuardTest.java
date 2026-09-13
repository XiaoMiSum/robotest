package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ModuleReferencedGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** 定时任务删除保护（定时任务详细设计 4.2）：all / modules / scenes 三种执行范围对场景与模块引用的判定 */
@ExtendWith(MockitoExtension.class)
class TestPlanSceneGuardTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SCENE_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID ANCESTOR_MODULE_ID = UUID.randomUUID();

    @Mock
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Mock
    private ApiSceneMapper sceneMapper;
    @Mock
    private ProjectModuleMapper moduleMapper;

    @InjectMocks
    private TestPlanSceneGuard guard;

    @Test
    void guardImplementsTcasedocPort() {
        assertTrue(guard instanceof ModuleReferencedGuard);
    }

    @Test
    void isSceneReferencedFalseWhenNoTasks() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID)).thenReturn(List.of());

        assertFalse(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isSceneReferencedTrueForAllScope() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("all", null, null)));

        assertTrue(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isSceneReferencedTrueWhenScenesScopeSelectsScene() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("scenes", List.of(SCENE_ID), null)));

        assertTrue(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isSceneReferencedFalseWhenScenesScopeNotContainScene() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("scenes", List.of(UUID.randomUUID()), null)));

        assertFalse(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isSceneReferencedTrueWhenModulesScopeSelectsAncestorModule() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("modules", null, List.of(ANCESTOR_MODULE_ID))));
        when(moduleMapper.selectById(MODULE_ID)).thenReturn(module(MODULE_ID, ANCESTOR_MODULE_ID));

        // 场景归属 MODULE_ID，其祖先模块被圈选（场景在其子模块下）→ 涵盖受保护
        assertTrue(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isSceneReferencedFalseWhenModulesScopeNotCoverSceneModule() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("modules", null, List.of(ANCESTOR_MODULE_ID))));
        when(moduleMapper.selectById(MODULE_ID)).thenReturn(null);

        assertFalse(guard.isSceneReferenced(PROJECT_ID, SCENE_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedFalseWithoutTasks() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID)).thenReturn(List.of());

        assertFalse(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedTrueForAllScope() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("all", null, null)));

        assertTrue(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedTrueWhenModulesScopeSelectsModuleItself() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("modules", null, List.of(MODULE_ID))));

        assertTrue(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedTrueWhenModulesScopeSelectsAncestor() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("modules", null, List.of(ANCESTOR_MODULE_ID))));
        when(moduleMapper.selectById(MODULE_ID)).thenReturn(module(MODULE_ID, ANCESTOR_MODULE_ID));

        assertTrue(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedTrueWhenScenesScopeSelectsSceneInModule() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("scenes", List.of(SCENE_ID), null)));
        ApiScene scene = new ApiScene();
        scene.setId(SCENE_ID);
        when(sceneMapper.listByModuleIds(PROJECT_ID, List.of(MODULE_ID))).thenReturn(List.of(scene));

        assertTrue(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    @Test
    void isModuleReferencedFalseWhenScenesScopeSelectsSceneInOtherModule() {
        when(scheduledTaskMapper.listTestPlanByProject(PROJECT_ID))
                .thenReturn(List.of(sceneTask("scenes", List.of(SCENE_ID), null)));
        ApiScene other = new ApiScene();
        other.setId(UUID.randomUUID());
        when(sceneMapper.listByModuleIds(PROJECT_ID, List.of(MODULE_ID))).thenReturn(List.of(other));

        assertFalse(guard.isModuleReferenced(PROJECT_ID, MODULE_ID));
    }

    private ApiScheduledTask sceneTask(String scope, List<UUID> sceneIds, List<UUID> moduleIds) {
        ApiScheduledTask task = new ApiScheduledTask();
        task.setProjectId(PROJECT_ID);
        task.setTaskType("scene_execute");
        task.setExecutionScope(scope);
        task.setSceneIds(sceneIds);
        task.setModuleIds(moduleIds);
        return task;
    }

    private ProjectModule module(UUID id, UUID parentId) {
        ProjectModule module = new ProjectModule();
        module.setId(id);
        module.setParentId(parentId);
        return module;
    }
}