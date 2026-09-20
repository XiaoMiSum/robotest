package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND;

/**
 * 场景批量移动编排器（接口测试域重构方案 04 §4.2，测试场景详细设计 3.1.7）。
 * <p>
 * 整体成功语义：先做存在性校验，任一场景不属于当前项目即整体拒绝。目标模块为空表示未分组。 */
public final class SceneMoveOrchestrator {

    private SceneMoveOrchestrator() {
    }

    public static void move(ApiSceneMapper sceneMapper, ProjectModuleMapper moduleMapper,
            UUID projectId, List<UUID> ids, UUID moduleId) {
        requireModuleInProject(moduleMapper, projectId, moduleId);
        for (UUID id : ids) {
            requireScene(sceneMapper, projectId, id);
        }
        for (UUID id : ids) {
            ApiScene update = new ApiScene();
            update.setId(id);
            update.setModuleId(moduleId);
            sceneMapper.updateById(update);
        }
    }

    /** 目标模块为空表示未分组；非空时须与场景同属当前项目 */
    private static void requireModuleInProject(ProjectModuleMapper moduleMapper, UUID projectId, UUID moduleId) {
        if (moduleId == null) {
            return;
        }
        ProjectModule module = moduleMapper.selectById(moduleId);
        if (module == null || !module.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(PROJECT_MODULE_NOT_FOUND);
        }
    }

    private static void requireScene(ApiSceneMapper sceneMapper, UUID projectId, UUID id) {
        ApiScene scene = sceneMapper.selectById(id);
        if (scene == null || !scene.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_SCENE_NOT_FOUND);
        }
    }
}