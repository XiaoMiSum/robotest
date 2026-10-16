package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 检索测试用例（详设 4.1）：按文档 / 项目 / 关键词定位 caseId，供更新、补全与圈选类动作引用。 */
@Component
public class QueryCasesTool implements AssistantTool {

    private static final int DOCS_MAX = 200;
    private static final int CASES_MAX = 50;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_cases",
                "按关键词检索测试用例，返回 caseId / 标题 / 所属文档与项目（可限定文档或项目）。"
                        + "需要引用用例 ID（caseIds、targetId）时调用，ID 只能取本工具返回值。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("keyword", "用例标题关键词，可空"),
                        ToolSchemas.string("documentId", "限定测试用例文档，可空"),
                        ToolSchemas.string("projectId", "限定项目，可空")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim();
        List<UUID> documentIds = resolveDocumentIds(context, args);
        if (documentIds.isEmpty()) {
            return JsonUtils.toJsonString(Map.of("count", 0, "cases", List.of()));
        }
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(CASES_MAX);
        PageResult<TestCaseNode> cases = testCaseNodeMapper.findCasePage(page, documentIds, keyword, null);
        Map<UUID, String> projectByDocument = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (TestCaseNode node : cases.getList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("caseId", String.valueOf(node.getId()));
            item.put("title", node.getTitle());
            item.put("documentId", String.valueOf(node.getDocumentId()));
            item.put("projectId", projectOf(node.getDocumentId(), projectByDocument));
            items.add(item);
        }
        return JsonUtils.toJsonString(Map.of("count", items.size(), "cases", items));
    }

    /** 文档限定 > 项目限定 > 页面附件聚焦 > 活跃空间全部文档（分页封顶），每级都过引用校验 */
    private List<UUID> resolveDocumentIds(AssistantToolContext context, Map<String, Object> args) {
        UUID documentId = uuid(args.get("documentId"));
        if (documentId != null) {
            requireDocumentInWorkspace(context.workspaceId(), documentId);
            return List.of(documentId);
        }
        UUID projectId = uuid(args.get("projectId"));
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(context.workspaceId(), projectId);
            return docIds(testCaseDocumentMapper.listByProjectId(projectId));
        }
        UUID focusDocumentId = focusDocumentId(context.attachments());
        if (focusDocumentId != null) {
            requireDocumentInWorkspace(context.workspaceId(), focusDocumentId);
            return List.of(focusDocumentId);
        }
        List<UUID> ids = new ArrayList<>();
        outer:
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            for (TestCaseDocument document : testCaseDocumentMapper.listByProjectId(project.getId())) {
                if (ids.size() >= DOCS_MAX) {
                    break outer;
                }
                ids.add(document.getId());
            }
        }
        return ids;
    }

    private void requireDocumentInWorkspace(UUID workspaceId, UUID documentId) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("document not found: " + documentId);
        }
        refChecker.requireActiveWorkspaceProject(workspaceId, document.getProjectId());
    }

    private String projectOf(UUID documentId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(documentId, key -> {
            TestCaseDocument document = testCaseDocumentMapper.selectById(key);
            if (document == null) {
                return "";
            }
            Project project = projectMapper.selectById(document.getProjectId());
            return project == null ? "" : String.valueOf(project.getId());
        });
    }

    private static List<UUID> docIds(List<TestCaseDocument> documents) {
        return documents.stream().map(TestCaseDocument::getId).toList();
    }

    private static UUID focusDocumentId(List<Map<String, Object>> attachments) {
        if (attachments == null) {
            return null;
        }
        for (Map<String, Object> attachment : attachments) {
            if (attachment == null
                    || !Constants.TraceNodeType.MINDMAP_DOCUMENT.equals(str(attachment.get("entityType")))) {
                continue;
            }
            UUID id = uuid(attachment.get("entityId"));
            if (id != null) {
                return id;
            }
        }
        return null;
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
