package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 查询项目文档树（详设 4.1）：模块 → 测试用例文档两级结构，供 create_case 精确指定落点与指代消解。 */
@Component
public class QueryDocTreeTool implements AssistantTool {

    private static final int MODULES_MAX = 50;
    private static final int DOCS_MAX = 100;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_doc_tree",
                "查询项目的「模块 → 测试用例文档」两级树（可按项目或名称关键词过滤）。"
                        + "创建 / 定位用例需要 documentId、parentId 时调用，ID 只能取本工具返回值。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("projectId", "目标项目 ID，可空表示活跃空间全部项目"),
                        ToolSchemas.string("keyword", "模块名或文档名关键词，可空")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim().toLowerCase(Locale.ROOT);
        UUID projectId = uuid(args.get("projectId"));
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(context.workspaceId(), projectId);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int docs = 0;
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            if (projectId != null && !project.getId().equals(projectId)) {
                continue;
            }
            Map<String, Object> tree = projectTree(project, keyword, docs);
            if (tree != null) {
                result.add(tree);
                docs += countDocs(tree);
            }
            if (docs >= DOCS_MAX) {
                break;
            }
        }
        return JsonUtils.toJsonString(Map.of("count", result.size(), "projects", result));
    }

    /**
     * 单项目组树：文档先按关键词预过滤；模块名命中关键词时保留该模块**全部**文档
     * （不二次过滤，便于一次看全落点）；空关键词时包含无文档的空模块。
     */
    private Map<String, Object> projectTree(Project project, String keyword, int docsSoFar) {
        List<TestCaseDocument> documents = testCaseDocumentMapper.listByProjectId(project.getId());
        List<TestCaseDocument> filtered = new ArrayList<>();
        for (TestCaseDocument document : documents) {
            if (filtered.size() >= DOCS_MAX - docsSoFar) {
                break;
            }
            String name = document.getName() == null ? "" : document.getName();
            if (keyword.isEmpty() || name.toLowerCase(Locale.ROOT).contains(keyword)) {
                filtered.add(document);
            }
        }
        Map<UUID, List<TestCaseDocument>> filteredByModule = groupByModule(filtered);

        List<Map<String, Object>> modules = new ArrayList<>();
        int moduleCount = 0;
        for (ProjectModule module : projectModuleMapper.listByProjectId(project.getId())) {
            if (moduleCount >= MODULES_MAX) {
                break;
            }
            String name = module.getName() == null ? "" : module.getName();
            boolean nameHit = !keyword.isEmpty() && name.toLowerCase(Locale.ROOT).contains(keyword);
            List<TestCaseDocument> moduleDocs = nameHit
                    ? docsOfModule(documents, module.getId())
                    : filteredByModule.getOrDefault(module.getId(), List.of());
            if (moduleDocs.isEmpty() && !nameHit && !keyword.isEmpty()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("moduleId", String.valueOf(module.getId()));
            entry.put("moduleName", name);
            entry.put("parentId", module.getParentId() == null ? null : String.valueOf(module.getParentId()));
            entry.put("documents", docEntries(moduleDocs));
            modules.add(entry);
            moduleCount++;
        }

        Map<String, Object> projectEntry = new LinkedHashMap<>();
        projectEntry.put("projectId", String.valueOf(project.getId()));
        projectEntry.put("projectName", project.getName());
        projectEntry.put("modules", modules);
        List<TestCaseDocument> rootDocs = filteredByModule.getOrDefault(null, List.of());
        if (!rootDocs.isEmpty()) {
            projectEntry.put("rootDocuments", docEntries(rootDocs));
        }
        return modules.isEmpty() && rootDocs.isEmpty() ? null : projectEntry;
    }

    private static Map<UUID, List<TestCaseDocument>> groupByModule(List<TestCaseDocument> documents) {
        Map<UUID, List<TestCaseDocument>> grouped = new LinkedHashMap<>();
        for (TestCaseDocument document : documents) {
            grouped.computeIfAbsent(document.getModuleId(), key -> new ArrayList<>()).add(document);
        }
        return grouped;
    }

    private static List<TestCaseDocument> docsOfModule(List<TestCaseDocument> documents, UUID moduleId) {
        List<TestCaseDocument> list = new ArrayList<>();
        for (TestCaseDocument document : documents) {
            if (moduleId != null && moduleId.equals(document.getModuleId())) {
                list.add(document);
            }
        }
        return list;
    }

    private static List<Map<String, Object>> docEntries(List<TestCaseDocument> documents) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TestCaseDocument document : documents) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("documentId", String.valueOf(document.getId()));
            entry.put("documentName", document.getName());
            list.add(entry);
        }
        return list;
    }

    private static int countDocs(Map<String, Object> projectTree) {
        int count = 0;
        if (projectTree.get("rootDocuments") instanceof List<?> root) {
            count += root.size();
        }
        if (projectTree.get("modules") instanceof List<?> modules) {
            for (Object module : modules) {
                if (module instanceof Map<?, ?> map && map.get("documents") instanceof List<?> docs) {
                    count += docs.size();
                }
            }
        }
        return count;
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }

    private static UUID uuid(Object value) {
        String text = str(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
