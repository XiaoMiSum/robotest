package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser.parseJsonObject;

/**
 * 分诊顺序建议处理器（缺陷分析详设 3.7）：范围固定为「激活未指派」缺陷，
 * 建议仅作排序展示，不写入任何数据（confirmStatus = not_applicable）。
 */
@Component
public class BugTriageHandler implements TaskHandler {

    public static final String TYPE = "bug_triage";

    private static final String SYSTEM_PROMPT = "你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";
    private static final String SCOPE = "active_unassigned";
    private static final String PERMISSION = "bug:view";
    /** 上下文容量护栏：超量缺陷只装配前 N 条进提示词（分诊建议只对可见缺陷排序） */
    private static final int MAX_CONTEXT_BUGS = 200;

    @Resource
    private BugMapper bugMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深缺陷分诊专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                对下列「激活且未指派」的缺陷给出建议处理顺序（rank 从 1 开始，1 为最先处理）：
                1. 优先考虑严重等级高、滞留时间长的缺陷；
                2. reason 必须基于清单中给出的信息，不得臆造；
                3. 每个缺陷恰好出现一次，不得引入清单外的缺陷。
                输出结构（JSON 对象，items 即排序结果）：
                {"items":[{"bugId":"…","rank":1,"reason":"…"}]}

                缺陷清单（id|标题|严重等级|优先级|模块|滞留天数）：
                {{bugContext}}
                """;
    }

    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    /** 输入范围固定，无入参可校验（详设 3.7） */
    @Override
    public void validateInput(Map<String, Object> input) {
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        context.report(10, "读取激活未指派缺陷");
        List<Bug> bugs = bugMapper.listActiveUnassigned(context.getProjectId());
        if (bugs.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_INPUT_EMPTY);
        }
        // 超量只装配前 N 条（创建早的在前），产物排序也仅覆盖这 N 条
        List<Bug> scoped = bugs.size() > MAX_CONTEXT_BUGS ? bugs.subList(0, MAX_CONTEXT_BUGS) : bugs;

        context.report(30, "构建提示词");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("bugContext", bugContext(scoped));
        String userPrompt = context.prompt(defaultPrompt(), variables);

        context.report(50, "模型生成分诊建议");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, userPrompt);

        context.report(85, "解析分诊建议");
        Map<String, Object> artifact = sanitize(reply, scoped);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", List.of(artifact));
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    private static String bugContext(List<Bug> bugs) {
        LocalDateTime now = LocalDateTime.now();
        return bugs.stream()
                .map(bug -> String.join("|",
                        bug.getId().toString(),
                        nvl(bug.getTitle()),
                        nvl(bug.getSeverity()),
                        nvl(bug.getPriority()),
                        nvl(moduleNameOf(bug)),
                        String.valueOf(ChronoUnit.DAYS.between(bug.getCreatedAt(), now))))
                .collect(Collectors.joining("\n"));
    }

    private static String moduleNameOf(Bug bug) {
        return bug.getModuleId() == null ? "" : bug.getModuleId().toString();
    }

    /** 清洗：剔除范围外缺陷、rank 重排为连续序号（同缺陷多次出现取首次） */
    private Map<String, Object> sanitize(AiChatReply reply, List<Bug> bugs) {
        Map<String, Object> parsed = asMap(parseJsonObject(reply.content()));
        Set<UUID> scope = bugs.stream().map(Bug::getId).collect(Collectors.toSet());

        List<Map<String, Object>> items = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        if (parsed.get("items") instanceof List<?> raw) {
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?>)) {
                    continue;
                }
                UUID bugId = null;
                try {
                    bugId = UUID.fromString(String.valueOf(asMap(element).get("bugId")).trim());
                } catch (RuntimeException e) {
                    continue;
                }
                if (!scope.contains(bugId) || !seen.add(bugId)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bugId", bugId.toString());
                item.put("rank", items.size() + 1);
                item.put("reason", String.valueOf(asMap(element).getOrDefault("reason", "")));
                items.add(item);
            }
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("items", items);
        content.put("scope", SCOPE);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "triage");
        artifact.put("kind", Constants.AiArtifactKind.BUG_TRIAGE);
        artifact.put("title", "分诊顺序建议");
        artifact.put("content", content);
        // 只读建议，不写入任何数据（详设 3.7）
        artifact.put("confirmStatus", "not_applicable");
        return artifact;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
