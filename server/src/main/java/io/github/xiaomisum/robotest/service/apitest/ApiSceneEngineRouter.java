package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;

/**
 * 场景执行引擎路由（GitLab 集成详设 4.1，SRS 3.4 BR-1.21/1.22）。
 * <p>
 * 引擎为派生属性，由场景来源与所属仓库导入模式共同决定，不接受用户选择：
 * 平台原生与 platform 模式导入场景恒由平台引擎执行，gitlab_ci 模式导入场景
 * 恒由流水线引擎执行；仓库配置已删除时无从判定流水线归属，回退平台引擎
 * （BR-1.4：删除配置后其下场景不可再由流水线引擎执行）。
 * <p>
 * 纯函数组件：无 Spring 依赖、无 I/O。
 */
public final class ApiSceneEngineRouter {

    public static final String ENGINE_PLATFORM = "platform";
    public static final String ENGINE_GITLAB_CI = "gitlab_ci";
    public static final String SOURCE_TYPE_GITLAB = "gitlab";
    public static final String IMPORT_MODE_GITLAB_CI = "gitlab_ci";

    private ApiSceneEngineRouter() {
    }

    /**
     * 判定场景执行引擎。
     *
     * @param scene 场景（sourceType 缺省视为平台原生）
     * @param repo  场景所属仓库；sourceType 非 gitlab 时可为 null，gitlab 场景仓库已删除时亦为 null
     * @return {@link #ENGINE_PLATFORM} 或 {@link #ENGINE_GITLAB_CI}
     */
    public static String resolve(ApiScene scene, ApiGitRepo repo) {
        if (scene == null || !SOURCE_TYPE_GITLAB.equals(scene.getSourceType())) {
            return ENGINE_PLATFORM;
        }
        if (repo == null) {
            return ENGINE_PLATFORM;
        }
        return IMPORT_MODE_GITLAB_CI.equals(repo.getImportMode()) ? ENGINE_GITLAB_CI : ENGINE_PLATFORM;
    }
}
