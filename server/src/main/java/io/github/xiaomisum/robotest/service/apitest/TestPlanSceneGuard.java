package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScheduledTask;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.apitest.ApiSceneMapper;
import io.github.xiaomisum.robotest.repository.apitest.ApiScheduledTaskMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ModuleReferencedGuard;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 定时任务删除保护（定时任务详细设计 4.2）：
 * 被测试计划任务引用的场景/模块禁止删除，引用关系按实时任务配置判定。
 * 实现 tcasedoc 域端口 ModuleReferencedGuard，供模块删除侧依赖（03 §4⑤）。
 */
@Service
public class TestPlanSceneGuard implements ModuleReferencedGuard {

    private static final String EXECUTION_SCOPE_ALL = "all";
    private static final String EXECUTION_SCOPE_MODULES = "modules";
    private static final String EXECUTION_SCOPE_SCENES = "scenes";

    @Resource
    private ApiScheduledTaskMapper scheduledTaskMapper;
    @Resource
    private ApiSceneMapper sceneMapper;
    @Resource
    private ProjectModuleMapper moduleMapper;

    /** 场景删除保护：被任务直接选中，或由 all / modules 执行范围涵盖 */
    public boolean isSceneReferenced(UUID projectId, UUID sceneId, UUID moduleId) {
        List<ApiScheduledTask> tasks = scheduledTaskMapper.listTestPlanByProject(projectId);
        for (ApiScheduledTask task : tasks) {
            if (coversScene(task, sceneId, moduleId)) {
                return true;
            }
        }
        return false;
    }

    /** 模块删除保护：模块（或其子模块）下存在被任务引用的场景 */
    @Override
    public boolean isModuleReferenced(UUID projectId, UUID moduleId) {
        List<ApiScheduledTask> tasks = scheduledTaskMapper.listTestPlanByProject(projectId);
        for (ApiScheduledTask task : tasks) {
            if (taskCoverageAppliesToModule(task, projectId, moduleId)) {
                return true;
            }
        }
        return false;
    }

    private boolean coversScene(ApiScheduledTask task, UUID sceneId, UUID moduleId) {
        String scope = task.getExecutionScope();
        if (EXECUTION_SCOPE_ALL.equals(scope)) {
            return true;
        }
        if (EXECUTION_SCOPE_SCENES.equals(scope)) {
            return task.getSceneIds() != null && task.getSceneIds().contains(sceneId);
        }
        if (EXECUTION_SCOPE_MODULES.equals(scope)) {
            return isModuleSelected(task.getModuleIds(), moduleId);
        }
        return false;
    }

    private boolean taskCoverageAppliesToModule(ApiScheduledTask task, UUID projectId, UUID moduleId) {
        String scope = task.getExecutionScope();
        if (EXECUTION_SCOPE_ALL.equals(scope)) {
            return true;
        }
        if (EXECUTION_SCOPE_MODULES.equals(scope)) {
            return isModuleSelected(task.getModuleIds(), moduleId);
        }
        if (EXECUTION_SCOPE_SCENES.equals(scope)) {
            return isSceneUnderModuleDirectlySelected(task, projectId, moduleId);
        }
        return false;
    }

    /**
     * modules 范围：模块自身或其任一祖先模块被任务选中即涵盖（其下场景全部被引用），
     * 沿 parentId 向上回溯避免每层递归查询
     */
    private boolean isModuleSelected(List<UUID> selectedModuleIds, UUID moduleId) {
        if (selectedModuleIds == null || moduleId == null) {
            return false;
        }
        UUID cursor = moduleId;
        while (cursor != null) {
            if (selectedModuleIds.contains(cursor)) {
                return true;
            }
            ProjectModule module = moduleMapper.selectById(cursor);
            cursor = module == null ? null : module.getParentId();
        }
        return false;
    }

    /** scenes 范围：模块下直接挂载的场景被任务直接选中时，模块下存在任务引用 */
    private boolean isSceneUnderModuleDirectlySelected(ApiScheduledTask task, UUID projectId, UUID moduleId) {
        if (task.getSceneIds() == null || moduleId == null) {
            return false;
        }
        List<UUID> sceneIdsUnderModule = sceneMapper.listByModuleIds(projectId, List.of(moduleId))
                .stream().map(ApiScene::getId).toList();
        return sceneIdsUnderModule.stream().anyMatch(task.getSceneIds()::contains);
    }
}