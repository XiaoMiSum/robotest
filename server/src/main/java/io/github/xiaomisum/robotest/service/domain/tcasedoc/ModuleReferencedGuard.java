package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import java.util.UUID;

/**
 * 模块引用守卫端口：测试计划定时任务圈选的模块（其下场景被任务引用）不可删除。
 * 由 apitest 域（TestPlanSceneGuard）实现，职责在 apitest 侧而依赖方向从 tcasedoc 指向
 * apitest 的端口，tcasedoc 域不 import apitest 实现（03 §4⑤）。
 */
public interface ModuleReferencedGuard {

    boolean isModuleReferenced(UUID projectId, UUID moduleId);
}