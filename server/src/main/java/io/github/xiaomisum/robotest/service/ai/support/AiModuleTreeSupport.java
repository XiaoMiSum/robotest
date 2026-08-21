package io.github.xiaomisum.robotest.service.ai.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 模块树支撑：模块树索引、路径构建与子树收集（MissingPoint / PlanOrder / CasePlanRecommend 共用）。
 * <p>
 * 操作 {@link ModuleTreeNode}（ProjectModule 与 TestCaseDocument 的统一投影），
 * 不再直接依赖已删除的 TestCaseModule 实体。
 */
public final class AiModuleTreeSupport {

    /** 模块树索引：moduleById + parentId → children（一次遍历构建，供子树遍历与路径解析复用） */
    public record ModuleIndex(Map<UUID, ModuleTreeNode> moduleById,
                              Map<UUID, List<ModuleTreeNode>> childrenByParent) {
    }

    private AiModuleTreeSupport() {
    }

    /** 模块树索引构建：一次遍历生成 moduleById 与 parentId → children 两份映射 */
    public static ModuleIndex indexByParent(List<ModuleTreeNode> modules) {
        Map<UUID, ModuleTreeNode> moduleById = new LinkedHashMap<>();
        Map<UUID, List<ModuleTreeNode>> childrenByParent = new LinkedHashMap<>();
        for (ModuleTreeNode module : modules) {
            moduleById.put(module.id(), module);
            childrenByParent.computeIfAbsent(module.parentId(), key -> new ArrayList<>()).add(module);
        }
        return new ModuleIndex(moduleById, childrenByParent);
    }

    /**
     * 模块树路径构建：目录链 + 文档名拼接为「目录A/目录B/文档」路径（响应 modulePath 口径）；
     * 模块树在排序上不保证父子先后，按需递归回溯并缓存。
     */
    public static Map<UUID, String> buildModulePaths(List<ModuleTreeNode> modules) {
        Map<UUID, String> pathById = new LinkedHashMap<>();
        Map<UUID, ModuleTreeNode> moduleById = new LinkedHashMap<>();
        modules.forEach(module -> moduleById.put(module.id(), module));
        for (ModuleTreeNode module : modules) {
            resolvePath(module.id(), moduleById, pathById);
        }
        return pathById;
    }

    private static String resolvePath(UUID moduleId, Map<UUID, ModuleTreeNode> moduleById,
                                      Map<UUID, String> pathById) {
        if (moduleId == null) {
            return "";
        }
        String cached = pathById.get(moduleId);
        if (cached != null) {
            return cached;
        }
        ModuleTreeNode module = moduleById.get(moduleId);
        if (module == null) {
            return "";
        }
        String parent = resolvePath(module.parentId(), moduleById, pathById);
        String path = parent.isEmpty() ? module.name() : parent + "/" + module.name();
        pathById.put(moduleId, path);
        return path;
    }

    /** 递归收集模块（含子孙目录）下全部文档 id；文档类型模块自身即文档 */
    public static void collectDocumentIds(UUID moduleId, Map<UUID, ModuleTreeNode> moduleById,
                                          Map<UUID, List<ModuleTreeNode>> childrenByParent, Set<UUID> out) {
        ModuleTreeNode module = moduleById.get(moduleId);
        if (module == null) {
            return;
        }
        if (module.isDocument()) {
            out.add(moduleId);
            return;
        }
        for (ModuleTreeNode child : childrenByParent.getOrDefault(moduleId, List.of())) {
            collectDocumentIds(child.id(), moduleById, childrenByParent, out);
        }
    }

    /** 递归收集模块（含子孙模块）id；文档类型模块为叶，子树即自身 */
    public static void collectSubtreeModuleIds(UUID moduleId, Map<UUID, ModuleTreeNode> moduleById,
                                               Map<UUID, List<ModuleTreeNode>> childrenByParent, Set<UUID> out) {
        if (!moduleById.containsKey(moduleId) || !out.add(moduleId)) {
            return;
        }
        for (ModuleTreeNode child : childrenByParent.getOrDefault(moduleId, List.of())) {
            collectSubtreeModuleIds(child.id(), moduleById, childrenByParent, out);
        }
    }
}
