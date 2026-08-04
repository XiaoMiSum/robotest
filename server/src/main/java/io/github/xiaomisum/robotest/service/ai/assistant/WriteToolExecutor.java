package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.service.project.BugService;
import io.github.xiaomisum.robotest.service.project.TestPlanService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 写工具执行器（详细设计 4.1 / 4.2）。
 *
 * <p>执行 create_bug / create_plan_draft 两项写操作；白名单过滤发生在工具清单组装时，
 * 此处仅负责参数映射与 Service 调用。执行结果以 JSON 返回，由调用方落库为 tool 消息。</p>
 */
@Component
public class WriteToolExecutor {

    @Resource
    private BugService bugService;
    @Resource
    private TestPlanService testPlanService;

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
