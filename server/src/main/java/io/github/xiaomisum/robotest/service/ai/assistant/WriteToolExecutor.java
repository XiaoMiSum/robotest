package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.domain.bug.BugService;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ProjectModuleService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseDocumentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 写工具执行器（详细设计 4.1 / 4.2 / 4.3）。
 *
 * <p>执行 create_bug / create_plan_draft / create_document / create_module 四项写操作；
 * 白名单过滤发生在工具清单组装时，此处仅负责参数映射与 Service 调用。
 * 执行结果以 JSON 返回，由调用方落库为 tool 消息。</p>
 */
@Component
public class WriteToolExecutor {

    @Resource
    private BugService bugService;
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private TestCaseDocumentService testCaseDocumentService;
    @Resource
    private ProjectModuleService projectModuleService;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    /**
     * 执行写工具
     *
     * @param context     工具上下文（userId/workspaceId/pageContext）
     * @param toolName    工具名
     * @param args        LLM 生成的参数
     * @return 执行结果 JSON 文本（含 routePath 供 LLM 生成跳转链接）
     */
    public String execute(AiToolContext context, String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "create_bug" -> executeCreateBug(context, args);
            case "create_plan_draft" -> executeCreatePlanDraft(context, args);
            case "create_document" -> executeCreateDocument(context, args);
            case "create_module" -> executeCreateModule(context, args);
            default -> "{\"error\":\"未知写工具: " + toolName + "\"}";
        };
    }

    private String executeCreateBug(AiToolContext context, Map<String, Object> args) {
        UUID projectId = uuidOrNull(args, "projectId");
        if (projectId == null) {
            projectId = projectIdFromContext(context);
        }
        if (projectId == null) {
            return "{\"error\":\"projectId 必填\"}";
        }

        BugCreateReqDTO reqDTO = new BugCreateReqDTO();
        reqDTO.setTitle(strOrEmpty(args, "title"));
        reqDTO.setSeverity(strOrEmpty(args, "severity"));
        reqDTO.setPriority(strOrEmpty(args, "priority"));
        reqDTO.setBugType(strOrDefault(args, "bugType", Constants.BugType.OTHER));
        reqDTO.setReproSteps(strOrNull(args, "reproSteps"));
        // assigneeId 缺省为当前用户（助手以 LoginUser 身份执行）
        UUID assigneeId = uuidOrNull(args, "assigneeId");
        if (assigneeId == null) {
            assigneeId = context.userId();
        }
        reqDTO.setAssigneeId(assigneeId);

        String bugId = bugService.createBug(projectId, context.userId(), reqDTO);
        return JsonUtils.toJsonString(Map.of(
                "id", bugId,
                "projectId", projectId.toString(),
                "title", reqDTO.getTitle(),
                "routePath", "/workspace/projects/bugs/" + bugId
        ));
    }

    private String executeCreatePlanDraft(AiToolContext context, Map<String, Object> args) {
        UUID projectId = uuidOrNull(args, "projectId");
        if (projectId == null) {
            projectId = projectIdFromContext(context);
        }
        if (projectId == null) {
            return "{\"error\":\"projectId 必填\"}";
        }

        TestPlanCreateReqDTO reqDTO = new TestPlanCreateReqDTO();
        reqDTO.setName(strOrEmpty(args, "name"));
        reqDTO.setDescription(strOrNull(args, "description"));
        // draft 模式：selectedNodes 传空列表，Service 层可接受（生成零快照）
        reqDTO.setSelectedNodes(List.of());

        var result = testPlanService.createPlan(projectId, context.userId(), reqDTO);
        return JsonUtils.toJsonString(Map.of(
                "id", result.getId().toString(),
                "projectId", projectId.toString(),
                "name", result.getName(),
                "status", "new",
                "routePath", "/workspace/projects/plans/" + result.getId()
        ));
    }

    private String executeCreateDocument(AiToolContext context, Map<String, Object> args) {
        UUID projectId = uuidOrNull(args, "projectId");
        if (projectId == null) {
            projectId = projectIdFromContext(context);
        }
        if (projectId == null) {
            return "{\"error\":\"projectId 必填\"}";
        }
        String documentName = strOrEmpty(args, "documentName");
        if (documentName.isBlank()) {
            return "{\"error\":\"documentName 必填\"}";
        }
        String moduleName = strOrNull(args, "moduleName");

        // 按名称匹配模块（可选）
        UUID moduleId = null;
        if (moduleName != null && !moduleName.isBlank()) {
            List<ProjectModule> modules = projectModuleMapper.listByProjectId(projectId);
            for (ProjectModule m : modules) {
                if (moduleName.equals(m.getName())) {
                    moduleId = m.getId();
                    break;
                }
            }
            if (moduleId == null) {
                return JsonUtils.toJsonString(Map.of(
                        "error", "未找到名称为「" + moduleName + "」的模块"));
            }
        }

        TestCaseDocumentCreateReqDTO reqDTO = new TestCaseDocumentCreateReqDTO();
        reqDTO.setModuleId(moduleId);
        reqDTO.setName(documentName);
        TestCaseDocumentRespDTO doc = testCaseDocumentService.createTestCase(projectId, context.userId(), reqDTO);

        // caseNodes 非空时在根节点下批量插入用例节点（4.3）
        int createdNodes = 0;
        Object caseNodesRaw = args.get("caseNodes");
        if (caseNodesRaw instanceof List<?> caseNodes && !caseNodes.isEmpty()) {
            // 查找根节点（parentId 为 null 的节点由 createTestCase 自动创建）
            List<TestCaseNode> existingNodes = testCaseNodeMapper.listByDocumentId(doc.getId());
            UUID rootNodeId = null;
            for (TestCaseNode n : existingNodes) {
                if (n.getParentId() == null) {
                    rootNodeId = n.getId();
                    break;
                }
            }
            if (rootNodeId != null) {
                List<TestCaseNode> nodes = new ArrayList<>();
                int sortOrder = 0;
                for (Object item : caseNodes) {
                    if (!(item instanceof Map<?, ?> caseNode)) {
                        continue;
                    }
                    String title = caseNode.get("title") instanceof String t ? t : "";
                    if (title.isBlank()) {
                        continue;
                    }
                    String priority = caseNode.get("priority") instanceof String p ? p : null;
                    TestCaseNode node = new TestCaseNode();
                    node.setDocumentId(doc.getId());
                    node.setParentId(rootNodeId);
                    node.setType(Constants.NodeType.CASE);
                    node.setTitle(title);
                    node.setPriority(priority);
                    node.setSortOrder(sortOrder++);
                    node.setVersion(1);
                    node.setAiGenerated(true);
                    nodes.add(node);
                }
                if (!nodes.isEmpty()) {
                    testCaseNodeMapper.insertBatch(nodes);
                    createdNodes = nodes.size();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", doc.getId().toString());
        result.put("documentName", doc.getName());
        result.put("projectId", projectId.toString());
        result.put("routePath", "/workspace/projects/test-case/" + doc.getId());
        result.put("createdNodes", createdNodes);
        return JsonUtils.toJsonString(result);
    }

    private String executeCreateModule(AiToolContext context, Map<String, Object> args) {
        UUID projectId = uuidOrNull(args, "projectId");
        if (projectId == null) {
            projectId = projectIdFromContext(context);
        }
        if (projectId == null) {
            return "{\"error\":\"projectId 必填\"}";
        }
        String moduleName = strOrEmpty(args, "moduleName");
        if (moduleName.isBlank()) {
            return "{\"error\":\"moduleName 必填\"}";
        }
        String parentModuleName = strOrNull(args, "parentModuleName");

        // 按名称匹配父模块（可选）
        UUID parentModuleId = null;
        if (parentModuleName != null && !parentModuleName.isBlank()) {
            List<ProjectModule> modules = projectModuleMapper.listByProjectId(projectId);
            for (ProjectModule m : modules) {
                if (parentModuleName.equals(m.getName())) {
                    parentModuleId = m.getId();
                    break;
                }
            }
            if (parentModuleId == null) {
                return JsonUtils.toJsonString(Map.of(
                        "error", "未找到名称为「" + parentModuleName + "」的父模块"));
            }
        }

        ProjectModuleCreateReqDTO reqDTO = new ProjectModuleCreateReqDTO();
        reqDTO.setParentId(parentModuleId);
        reqDTO.setName(moduleName);
        ProjectModuleTreeRespDTO module = projectModuleService.createModule(projectId, context.userId(), reqDTO);

        return JsonUtils.toJsonString(Map.of(
                "moduleId", module.getId().toString(),
                "moduleName", module.getName(),
                "projectId", projectId.toString()
        ));
    }

    /** pageContext 中的 projectId 兜底 */
    private UUID projectIdFromContext(AiToolContext context) {
        if (context.pageContext() == null) {
            return null;
        }
        Object val = context.pageContext().get("projectId");
        if (val instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private String strOrEmpty(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val instanceof String s ? s : "";
    }

    private String strOrNull(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val instanceof String s ? s : null;
    }

    private String strOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private UUID uuidOrNull(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val instanceof String s && !s.isBlank()) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
