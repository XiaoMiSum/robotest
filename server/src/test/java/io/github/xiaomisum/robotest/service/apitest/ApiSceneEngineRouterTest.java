package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import io.github.xiaomisum.robotest.model.entity.apitest.ApiScene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 引擎路由判定测试：三类输入（平台原生 / platform 导入 / gitlab_ci 导入）与
 * 仓库缺失回退（详设 4.1，BR-1.21/1.22/1.4）。
 */
class ApiSceneEngineRouterTest {

    private static ApiScene scene(String sourceType) {
        ApiScene scene = new ApiScene();
        scene.setSourceType(sourceType);
        return scene;
    }

    private static ApiGitRepo repo(String importMode) {
        ApiGitRepo repo = new ApiGitRepo();
        repo.setImportMode(importMode);
        return repo;
    }

    @Test
    void platformNativeRoutesToPlatform() {
        assertEquals("platform", ApiSceneEngineRouter.resolve(scene("platform"), null));
    }

    @Test
    void nullSourceTypeDefaultsToPlatform() {
        // source_type 列缺省 'platform'（详设 2.1.2），实体未回填时同样落平台组
        assertEquals("platform", ApiSceneEngineRouter.resolve(scene(null), repo("gitlab_ci")));
    }

    @Test
    void platformImportRoutesToPlatformEvenWhenStepsEmpty() {
        // BR-1.21：platform 模式导入场景不提供切换到流水线引擎的入口
        assertEquals("platform", ApiSceneEngineRouter.resolve(scene("gitlab"), repo("platform")));
    }

    @Test
    void gitlabCiImportRoutesToPipeline() {
        // BR-1.22：gitlab_ci 模式导入场景恒由流水线引擎执行
        assertEquals("gitlab_ci", ApiSceneEngineRouter.resolve(scene("gitlab"), repo("gitlab_ci")));
    }

    @Test
    void deletedRepoFallsBackToPlatform() {
        // BR-1.4：仓库配置删除后其下场景不可再由流水线引擎执行
        assertEquals("platform", ApiSceneEngineRouter.resolve(scene("gitlab"), null));
    }

    @Test
    void unknownImportModeRoutesToPlatform() {
        assertEquals("platform", ApiSceneEngineRouter.resolve(scene("gitlab"), repo("other")));
    }

    @Test
    void nullSceneRoutesToPlatform() {
        assertEquals("platform", ApiSceneEngineRouter.resolve(null, null));
    }
}
