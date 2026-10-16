package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 变更类工具入参 → intent 固化（详设 4.1 拦截规范化、4.2 / 4.3）：kind 取工具名（服务端固化，
 * 不信赖模型输出），字段白名单、引用越权校验、scope 与 10 分钟过期由服务端补齐；
 * 全程零业务写入——落库只发生在用户确认后的既有服务链路（4.2①）。
 */
@Component
public class AssistantIntentBuilder {

    private static final Set<String> INTENT_KINDS = Set.copyOf(AssistantToolRegistry.CHANGE_KINDS);

    private static final Map<String, String> TARGET_TYPE_BY_KIND = Map.ofEntries(
            Map.entry("create_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("update_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("complete_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("batch_tag", Constants.TraceNodeType.TEST_CASE),
            Map.entry("create_review", Constants.TraceNodeType.TEST_REVIEW),
            Map.entry("adjust_review", Constants.TraceNodeType.TEST_REVIEW),
            Map.entry("create_plan", Constants.TraceNodeType.TEST_PLAN),
            Map.entry("adjust_plan", Constants.TraceNodeType.TEST_PLAN));

    private static final int CASE_IDS_MAX = 200;
    private static final int CHANGES_MAX = 30;
    private static final int TITLE_MAX = 100;
    /** 预览时效：生成后 10 分钟过期（详设 4.3 expiresAt） */
    private static final int EXPIRE_MINUTES = 10;

    @Resource
    private AssistantRefChecker refChecker;
    @Resource
    private AssistantPromptSupport promptSupport;

    /**
     * 规范化（详设 4.2/4.3）：动作与目标类型服务端判定，引用经活跃工作空间校验
     * （越权或幻觉按 1000018261 拒绝），scope 与过期时间由服务端补齐。
     *
     * @param toolName         变更类工具名（= intent kind，注册表已校验）
     * @param raw              模型入参（结构同 intent：targetTitle / targetId / projectId / params / changes）
     * @param activeWorkspaceId 活跃工作空间（解析时点，X-Active-Workspace）
     */
    public Map<String, Object> normalize(String toolName, Map<String, Object> raw, UUID activeWorkspaceId) {
        String kind = ModelOutputSupport.trimToNull(toolName);
        if (kind == null || !INTENT_KINDS.contains(kind) || activeWorkspaceId == null || raw == null) {
            throw parseFailed();
        }
        String targetType = TARGET_TYPE_BY_KIND.get(kind);
        UUID targetId = parseUuid(raw.get("targetId"));
        UUID projectId = parseUuid(raw.get("projectId"));
        Set<UUID> caseIds = parseCaseIds(ModelOutputSupport.asMap(raw.get("params")).get("caseIds"));

        if (targetId != null) {
            refChecker.requireActiveWorkspaceRef(activeWorkspaceId, targetType, targetId);
        }
        Set<UUID> caseProjects = refChecker.requireActiveWorkspaceCases(activeWorkspaceId, caseIds);
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(activeWorkspaceId, projectId);
        }
        return assemble(raw, kind, targetType, targetId, projectId, caseIds, caseProjects, activeWorkspaceId);
    }

    /** 作用域项目：用例集（须同项目）> 目标对象所属项目 > 指定项目，皆无则解析失败 */
    private Map<String, Object> assemble(Map<String, Object> raw, String kind, String targetType, UUID targetId,
            UUID projectId, Set<UUID> caseIds, Set<UUID> caseProjects, UUID activeWorkspaceId) {
        UUID scopeProjectId = null;
        if (!caseProjects.isEmpty()) {
            if (caseProjects.size() != 1) {
                throw parseFailed();
            }
            scopeProjectId = caseProjects.iterator().next();
        }
        if (scopeProjectId == null && targetId != null) {
            scopeProjectId = refChecker.projectIdOf(targetType, targetId);
        }
        if (scopeProjectId == null) {
            scopeProjectId = projectId;
        }
        if (scopeProjectId == null) {
            throw parseFailed();
        }
        // 模型同时给出的目标项目与用例 / 目标对象所属项目冲突：视为解析产物不自洽
        if (projectId != null && !projectId.equals(scopeProjectId)) {
            throw parseFailed();
        }
        String targetTitle = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw.get("targetTitle")));
        if (targetTitle == null && targetId != null) {
            targetTitle = refChecker.titleOf(targetType, targetId);
        }
        if (targetTitle == null) {
            throw parseFailed();
        }

        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("kind", kind);
        intent.put("targetType", targetType);
        intent.put("targetTitle", ModelOutputSupport.abbreviate(targetTitle, TITLE_MAX));
        if (targetId != null) {
            intent.put("targetId", targetId);
        }
        intent.put("projectId", scopeProjectId);
        intent.put("createCount", sanitizedCount(raw.get("createCount"), kind, raw));
        intent.put("changes", sanitizedChanges(raw.get("changes")));
        intent.put("params", sanitizedParams(raw.get("params"), caseIds));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("workspaceId", activeWorkspaceId);
        scope.put("projectId", scopeProjectId);
        scope.put("projectName", promptSupport.projectName(scopeProjectId));
        intent.put("scope", scope);
        // 与 jsonb 内时间同口径：ISO-8601 字符串（详设 4.3 expiresAt 默认 10 分钟）
        intent.put("expiresAt", LocalDateTime.now().plusMinutes(EXPIRE_MINUTES).toString());
        return intent;
    }

    private static List<Map<String, Object>> sanitizedChanges(Object raw) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return changes;
        }
        for (Object element : list) {
            if (changes.size() >= CHANGES_MAX) {
                break;
            }
            Map<String, Object> item = ModelOutputSupport.asMap(element);
            String field = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(item.get("field")));
            if (field == null) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", ModelOutputSupport.abbreviate(field, TITLE_MAX));
            change.put("op", "replace".equals(ModelOutputSupport.asString(item.get("op"))) ? "replace" : "add");
            change.put("value", item.get("value") instanceof String value
                    ? ModelOutputSupport.abbreviate(value, 1000) : item.get("value"));
            changes.add(change);
        }
        return changes;
    }

    private static Map<String, Object> sanitizedParams(Object raw, Set<UUID> caseIds) {
        // 复制后再清洗：模型入参可能来自不可变 Map，且不得回写原始入参
        Map<String, Object> source = ModelOutputSupport.asMap(raw);
        Map<String, Object> params = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        // caseIds 统一以清洗后的 UUID 集回填，字符串引用与幻觉项在此收敛
        params.remove("caseIds");
        params.replaceAll((key, value) -> value instanceof String text
                ? ModelOutputSupport.abbreviate(text, 2000) : value);
        if (!caseIds.isEmpty()) {
            params.put("caseIds", List.copyOf(caseIds));
        }
        return params;
    }

    /**
     * 预览数量：模型显式给出则钳位；创建类缺省时按入参派生（create_case 取 cases 条数，
     * 其余创建类为 1），非创建类恒 0——不依赖模型回显计数（详设 4.3 createCount）。
     */
    private static int sanitizedCount(Object raw, String kind, Map<String, Object> rawIntent) {
        if (raw instanceof Number number) {
            return Math.max(0, Math.min(number.intValue(), 10_000));
        }
        if (!kind.startsWith("create_")) {
            return 0;
        }
        if (kind.equals("create_case")
                && ModelOutputSupport.asMap(rawIntent.get("params")).get("cases") instanceof List<?> cases
                && !cases.isEmpty()) {
            return Math.min(cases.size(), 10_000);
        }
        return 1;
    }

    private static Set<UUID> parseCaseIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<UUID> caseIds = new java.util.LinkedHashSet<>();
        for (Object element : list) {
            if (caseIds.size() >= CASE_IDS_MAX) {
                break;
            }
            UUID caseId = parseUuid(element);
            if (caseId != null) {
                caseIds.add(caseId);
            }
        }
        return caseIds;
    }

    private static UUID parseUuid(Object raw) {
        String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw));
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw parseFailed();
        }
    }

    private static ServiceException parseFailed() {
        return ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
    }
}
