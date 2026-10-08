package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser.parseJsonObject;

/**
 * 存量重复扫描处理器（缺陷分析详设 3.9）：向量检索为候选生成信号、模型归并分组；
 * 产物为疑似重复分组（confirmStatus = pending），确认仅留痕、不修改缺陷（详设 3.9 / 4.2）。
 */
@Component
public class BugDuplicateScanHandler implements TaskHandler {

    public static final String TYPE = "bug_duplicate_scan";

    private static final String SYSTEM_PROMPT = "你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";
    private static final String PERMISSION = "bug:view";
    private static final String SCOPE_ACTIVE = "active";
    /** 扫描范围上限：超量只扫描创建最早的前 N 条（护栏，避免全量缺陷进提示词） */
    private static final int SCAN_LIMIT = 200;
    private static final int CANDIDATE_LIMIT = 5;

    @Resource
    private BugMapper bugMapper;
    @Resource
    private VectorSearchService vectorSearchService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                根据缺陷清单与相似候选，找出疑似重复的缺陷分组：
                1. 仅当两条缺陷描述的是同一问题（同现象、同根因）时才可归入同组；
                2. 每组给出 canonicalBugId（该组中最早创建的缺陷）与 items（组内其余缺陷及理由）；
                3. 每个缺陷最多出现在一个分组中；无把握时不分组，输出空 groups 数组，不得臆造。
                输出结构（JSON 对象，groups 即分组清单）：
                {"groups":[{"canonicalBugId":"…","items":[{"bugId":"…","similarity":0.83,"reason":"…"}]}]}

                缺陷清单（id|创建时间|标题）：
                {{bugContext}}
                """;
    }

    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    /** 仅支持 scope = active（详设 3.9）；其余取值 1000018286 */
    @Override
    public void validateInput(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("scope");
        String scope = raw == null || String.valueOf(raw).isBlank() ? SCOPE_ACTIVE : String.valueOf(raw).trim();
        if (!SCOPE_ACTIVE.equals(scope)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_SCOPE_INVALID);
        }
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        context.report(10, "读取扫描范围");
        List<Bug> bugs = bugMapper.listByStatus(context.getProjectId(), Constants.BugStatus.ACTIVE, SCAN_LIMIT);
        if (bugs.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_INPUT_EMPTY);
        }

        context.report(30, "检索相似候选");
        Map<UUID, List<Hit>> candidates = candidatePairs(bugs, context.getProjectId(), context.getUserId());

        context.report(50, "模型归并分组");
        Map<String, String> variables = new HashMap<>();
        variables.put("bugContext", bugContext(bugs, candidates));
        AiChatReply reply = context.chat(SYSTEM_PROMPT, context.prompt(defaultPrompt(), variables));

        context.report(85, "解析重复分组");
        List<Map<String, Object>> artifacts = sanitize(reply, bugs);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    private record Hit(UUID bugId, double similarity) {
    }

    /** 逐缺陷向量检索相似候选（详设 3.8 同口径的项目内 bug 检索）；向量未就绪降级为无候选（模型仅按文本判断） */
    private Map<UUID, List<Hit>> candidatePairs(List<Bug> bugs, UUID projectId, UUID userId) {
        Map<UUID, List<Hit>> pairs = new LinkedHashMap<>();
        for (Bug bug : bugs) {
            String query = bug.getTitle() == null ? "" : bug.getTitle();
            if (query.isBlank()) {
                continue;
            }
            List<AiVectorSearchHitRespDTO> hits;
            try {
                hits = vectorSearchService.search(query, List.of(projectId), VectorIndexService.TYPE_BUG,
                        CANDIDATE_LIMIT + 1, userId);
            } catch (ServiceException e) {
                return Map.of();
            }
            List<Hit> filtered = hits.stream()
                    .filter(hit -> hit.getEntityId() != null && !hit.getEntityId().equals(bug.getId()))
                    .filter(hit -> bugs.stream().anyMatch(scope -> scope.getId().equals(hit.getEntityId())))
                    .map(hit -> new Hit(hit.getEntityId(),
                            hit.getDistance() == null ? null : 1 - hit.getDistance()))
                    .limit(CANDIDATE_LIMIT)
                    .toList();
            if (!filtered.isEmpty()) {
                pairs.put(bug.getId(), filtered);
            }
        }
        return pairs;
    }

    private static String bugContext(List<Bug> bugs, Map<UUID, List<Hit>> candidates) {
        Map<UUID, Bug> scope = bugs.stream()
                .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));
        return bugs.stream()
                .map(bug -> {
                    StringBuilder line = new StringBuilder()
                            .append(bug.getId()).append('|')
                            .append(bug.getCreatedAt()).append('|')
                            .append(bug.getTitle() == null ? "" : bug.getTitle());
                    List<Hit> hits = candidates.get(bug.getId());
                    if (hits != null && !hits.isEmpty()) {
                        line.append("|相似候选：").append(hits.stream()
                                .map(hit -> scope.get(hit.bugId()).getTitle() + "(相似度约 "
                                        + String.format("%.2f", hit.similarity()) + ")")
                                .collect(Collectors.joining("、")));
                    }
                    return line.toString();
                })
                .collect(Collectors.joining("\n"));
    }

    /** 清洗：分组键连续编号；canonical 与 items 必须在扫描范围内、组内去重、跨组去重（后组丢弃已出现缺陷） */
    private List<Map<String, Object>> sanitize(AiChatReply reply, List<Bug> bugs) {
        Map<String, Object> parsed = asMap(parseJsonObject(reply.content()));
        Map<UUID, Bug> scope = bugs.stream()
                .collect(Collectors.toMap(Bug::getId, bug -> bug, (left, right) -> left));
        Set<UUID> assigned = new LinkedHashSet<>();

        List<Map<String, Object>> artifacts = new ArrayList<>();
        if (parsed.get("groups") instanceof List<?> raw) {
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> group = asMap(element);
                UUID canonicalId = parseId(group.get("canonicalBugId"));
                Bug canonical = canonicalId == null ? null : scope.get(canonicalId);
                if (canonical == null || assigned.contains(canonicalId)) {
                    continue;
                }
                List<Map<String, Object>> items = new ArrayList<>();
                if (group.get("items") instanceof List<?> rawItems) {
                    for (Object itemElement : rawItems) {
                        if (!(itemElement instanceof Map<?, ?>)) {
                            continue;
                        }
                        Map<String, Object> rawItem = asMap(itemElement);
                        UUID bugId = parseId(rawItem.get("bugId"));
                        if (bugId == null || bugId.equals(canonicalId) || !scope.containsKey(bugId)
                                || !assigned.add(bugId)) {
                            continue;
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("bugId", bugId.toString());
                        Object similarity = rawItem.get("similarity");
                        if (similarity instanceof Number number) {
                            item.put("similarity", Math.round(number.doubleValue() * 100.0) / 100.0);
                        }
                        item.put("reason", Objects.toString(rawItem.get("reason"), ""));
                        items.add(item);
                    }
                }
                if (items.isEmpty()) {
                    continue;
                }
                assigned.add(canonicalId);

                Map<String, Object> content = new LinkedHashMap<>();
                content.put("canonicalBugId", canonicalId.toString());
                content.put("items", items);

                Map<String, Object> artifact = new LinkedHashMap<>();
                artifact.put("key", "group-" + (artifacts.size() + 1));
                artifact.put("kind", Constants.AiArtifactKind.BUG_DUPLICATE_GROUP);
                artifact.put("title", "疑似重复分组（" + (items.size() + 1) + " 条）");
                artifact.put("content", content);
                // 人工确认分组结论，检测结果不自动合并、不改状态（详设 3.9）
                artifact.put("confirmStatus", "pending");
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    private static UUID parseId(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
