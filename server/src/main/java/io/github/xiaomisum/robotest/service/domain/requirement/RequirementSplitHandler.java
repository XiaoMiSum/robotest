package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 条目内拆分任务处理器（详设 3.9 / 4.2）：读取原需求与模块树 → 渲染提示词 → 调模型 → 产物为
 * requirement_suggestion 列表（result.artifacts，单一事实源，详设 2.6）。
 */
@Component
public class RequirementSplitHandler implements TaskHandler {

    private static final String TYPE = "requirement_split";
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

    private static final String SYSTEM_PROMPT = "你是严谨的需求分析助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";

    private static final String DEFAULT_PROMPT = """
            将给定需求拆分为多条粒度更细、可独立测试与验收的需求建议。
            要求：
            1. 每条建议给出标题、描述（Markdown，含可验证的验收要点）、所属模块与优先级；
            2. 标题互不重复且具体，描述基于原文改写，不得臆造原文没有的业务规则；
            3. 拆分粒度以「单条可独立测试」为准，原文已足够细时可少拆甚至不拆；
            4. 仅当原文明确属于某模块时给出该模块 id，否则 moduleId 为 null；
            5. 优先级只允许 high / medium / low。
            输出结构（JSON 对象，artifacts 数组即产物清单）：
            {"artifacts":[{"content":{"title":"…","description":"…","moduleId":null,"priority":"medium"}}]}

            需求编号：{{requirementCode}}
            需求标题：{{requirementTitle}}
            需求描述：
            {{requirementDescription}}
            可用模块（id|名称）：
            {{moduleOptions}}
            """;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return DEFAULT_PROMPT;
    }

    /** 拆分入口在需求侧已校验 requirement:edit（详设 3.9 权限表）；直提任务资源时在此补齐（3.6.2） */
    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains("requirement:edit")) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION);
        }
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        parseRequirementId(input);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        UUID requirementId = parseRequirementId(context.getInput());
        context.report(10, "读取需求与模块");
        Requirement item = requirementMapper.selectById(requirementId);
        if (item == null || !Objects.equals(item.getProjectId(), context.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NOT_FOUND);
        }
        if (Constants.RequirementStatus.ARCHIVED.equals(item.getStatus())) {
            // 提交与执行之间条目被归档：归档条目不再作为 AI 输入（3.7）
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_SPLIT_INPUT_INVALID);
        }

        context.report(30, "构建提示词");
        List<ProjectModule> modules = projectModuleMapper.listByProjectId(context.getProjectId());
        Map<String, String> variables = new HashMap<>();
        variables.put("requirementCode", nvl(item.getCode()));
        variables.put("requirementTitle", nvl(item.getTitle()));
        variables.put("requirementDescription", nvl(item.getDescription()));
        variables.put("moduleOptions", moduleOptions(modules));
        String userPrompt = context.prompt(DEFAULT_PROMPT, variables);

        context.report(50, "模型生成拆分建议");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, userPrompt);

        context.report(85, "解析拆分建议");
        List<Map<String, Object>> artifacts = sanitize(parse(reply.content()), modules);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    // ---------- 输入解析与产物清洗 ----------

    private static UUID parseRequirementId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("requirementId");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 解析模型输出：容忍 ```json 代码栅栏；不可解析按模型调用失败（1000018117）落任务失败态 */
    private static List<?> parse(String content) {
        String text = stripCodeFence(content);
        Map<String, Object> parsed;
        try {
            parsed = JsonUtils.parseObject(text, Map.class);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "模型输出无法解析为 JSON");
        }
        if (parsed == null || !(parsed.get("artifacts") instanceof List<?> list)) {
            return List.of();
        }
        return list;
    }

    private static String stripCodeFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstLineEnd + 1);
        int closing = body.lastIndexOf("```");
        if (closing >= 0) {
            body = body.substring(0, closing);
        }
        return body.trim();
    }

    /**
     * 产物清洗：标题缺失（空白）的建议直接丢弃；moduleId 必须是项目内模块否则置 null；
     * priority 非法置 null。key 统一定序为 item-N，保证确认引用稳定。
     */
    private static List<Map<String, Object>> sanitize(List<?> rawArtifacts, List<ProjectModule> modules) {
        Set<UUID> moduleIds = modules.stream().map(ProjectModule::getId).collect(Collectors.toSet());
        List<Map<String, Object>> artifacts = new ArrayList<>();
        int seq = 0;
        for (Object element : rawArtifacts) {
            if (!(element instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> content = new LinkedHashMap<>();
            if (raw.get("content") instanceof Map<?, ?> rawContent) {
                rawContent.forEach((k, v) -> content.put(String.valueOf(k), v));
            } else {
                // 容忍扁平结构（模型未按 content 包裹）：直接取顶层字段
                raw.forEach((k, v) -> content.put(String.valueOf(k), v));
            }
            String title = asString(content.get("title"));
            if (title == null || title.isBlank()) {
                continue;
            }
            content.put("title", title.trim());
            String description = asString(content.get("description"));
            content.put("description", description == null || description.isBlank() ? null : description);
            content.put("moduleId", normalizeModuleId(content.get("moduleId"), moduleIds));
            content.put("priority", normalizePriority(content.get("priority")));

            Map<String, Object> artifact = new LinkedHashMap<>();
            seq++;
            artifact.put("key", "item-" + seq);
            artifact.put("kind", "requirement_suggestion");
            artifact.put("title", content.get("title"));
            artifact.put("content", content);
            String sourceRef = asString(raw.get("sourceRef"));
            if (sourceRef != null && !sourceRef.isBlank()) {
                artifact.put("sourceRef", sourceRef.trim());
            }
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private static Object normalizeModuleId(Object raw, Set<UUID> moduleIds) {
        String value = asString(raw);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(value.trim());
            return moduleIds.contains(id) ? id.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizePriority(Object raw) {
        String value = asString(raw);
        return value != null && PRIORITIES.contains(value.trim()) ? value.trim() : null;
    }

    private static String moduleOptions(List<ProjectModule> modules) {
        if (modules.isEmpty()) {
            return "（当前项目暂无模块，moduleId 一律为 null）";
        }
        StringBuilder builder = new StringBuilder();
        for (ProjectModule module : modules) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(module.getId()).append('|').append(module.getName());
        }
        return builder.toString();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
