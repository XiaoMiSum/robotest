package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 模块树支撑：模块树索引、路径构建与子树收集（Regression / MissingPoint / PlanOrder 共用）。
 */
public final class AiModuleTreeSupport {

    /** 模块树索引：moduleById + parentId → children（一次遍历构建，供子树遍历与路径解析复用） */
    public record ModuleIndex(Map<UUID, TestCaseModule> moduleById,
                              Map<UUID, List<TestCaseModule>> childrenByParent) {
    }

    private AiModuleTreeSupport() {
    }

    /** 模块树索引构建：一次遍历生成 moduleById 与 parentId → children 两份映射 */
    public static ModuleIndex indexByParent(List<TestCaseModule> modules) {
        Map<UUID, TestCaseModule> moduleById = new LinkedHashMap<>();
        Map<UUID, List<TestCaseModule>> childrenByParent = new LinkedHashMap<>();
        for (TestCaseModule module : modules) {
            moduleById.put(module.getId(), module);
            childrenByParent.computeIfAbsent(module.getParentId(), key -> new ArrayList<>()).add(module);
        }
        return new ModuleIndex(moduleById, childrenByParent);
    }

    /**
     * 模块树路径构建：目录链 + 文档名拼接为「目录A/目录B/文档」路径（响应 modulePath 口径）；
     * 模块树在排序上不保证父子先后，按需递归回溯并缓存。
     */
    public static Map<UUID, String> buildModulePaths(List<TestCaseModule> modules) {
        Map<UUID, String> pathById = new LinkedHashMap<>();
        Map<UUID, TestCaseModule> moduleById = new LinkedHashMap<>();
        modules.forEach(module -> moduleById.put(module.getId(), module));
        for (TestCaseModule module : modules) {
            resolvePath(module.getId(), moduleById, pathById);
        }
        return pathById;
    }

    private static String resolvePath(UUID moduleId, Map<UUID, TestCaseModule> moduleById,
                                      Map<UUID, String> pathById) {
        if (moduleId == null) {
            return "";
        }
        String cached = pathById.get(moduleId);
        if (cached != null) {
            return cached;
        }
        TestCaseModule module = moduleById.get(moduleId);
        if (module == null) {
            return "";
        }
        String parent = resolvePath(module.getParentId(), moduleById, pathById);
        String path = parent.isEmpty() ? module.getName() : parent + "/" + module.getName();
        pathById.put(moduleId, path);
        return path;
    }

    /** 递归收集模块（含子孙目录）下全部文档 id；文档类型模块自身即文档 */
    public static void collectDocumentIds(UUID moduleId, Map<UUID, TestCaseModule> moduleById,
                                          Map<UUID, List<TestCaseModule>> childrenByParent, Set<UUID> out) {
        TestCaseModule module = moduleById.get(moduleId);
        if (module == null) {
            return;
        }
        if (Constants.ModuleType.DOCUMENT.equals(module.getType())) {
            out.add(moduleId);
            return;
        }
        for (TestCaseModule child : childrenByParent.getOrDefault(moduleId, List.of())) {
            collectDocumentIds(child.getId(), moduleById, childrenByParent, out);
        }
    }

    /** 递归收集模块（含子孙模块）id；文档类型模块为叶，子树即自身 */
    public static void collectSubtreeModuleIds(UUID moduleId, Map<UUID, TestCaseModule> moduleById,
                                               Map<UUID, List<TestCaseModule>> childrenByParent, Set<UUID> out) {
        if (!moduleById.containsKey(moduleId) || !out.add(moduleId)) {
            return;
        }
        for (TestCaseModule child : childrenByParent.getOrDefault(moduleId, List.of())) {
            collectSubtreeModuleIds(child.getId(), moduleById, childrenByParent, out);
        }
    }
}
