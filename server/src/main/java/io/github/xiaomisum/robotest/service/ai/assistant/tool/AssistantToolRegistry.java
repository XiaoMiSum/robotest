package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（详设 4.1：服务端固化 16 个，清单外调用按 1000018255 拒绝）。
 *
 * <p>QUERY / CLARIFY 经 Spring 收集 {@link AssistantTool} Bean 注册（新增执行体自动入表）；
 * CHANGE 8 项为 definition-only——无执行体，调用即转 {@link AssistantIntentBuilder} 拦截规范化。</p>
 */
@Component
public class AssistantToolRegistry {

    /** 变更类 8 种 kind（详设 4.3，与执行侧 INTENT_KINDS 同口径；进度查看归只读问答不在此列） */
    public static final List<String> CHANGE_KINDS = List.of(
            "create_case", "update_case", "complete_case", "batch_tag",
            "create_review", "adjust_review", "create_plan", "adjust_plan");

    private final Map<String, AssistantToolDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AssistantTool> executables = new LinkedHashMap<>();

    public AssistantToolRegistry(List<AssistantTool> registered) {
        for (AssistantTool tool : registered) {
            AssistantToolDefinition definition = tool.definition();
            definitions.put(definition.name(), definition);
            executables.put(definition.name(), tool);
        }
        for (String kind : CHANGE_KINDS) {
            definitions.put(kind, changeDefinition(kind));
        }
    }

    /** 清单外返回 null，由循环按 1000018255 拒绝 */
    public AssistantToolDefinition definition(String name) {
        return name == null ? null : definitions.get(name);
    }

    /** 可执行体（QUERY / CLARIFY）；变更类恒为 null（执行即拦截） */
    public AssistantTool executable(String name) {
        return name == null ? null : executables.get(name);
    }

    public Collection<AssistantToolDefinition> all() {
        return definitions.values();
    }

    // ---------- 变更类定义（description 即给模型的入参契约） ----------

    private static AssistantToolDefinition changeDefinition(String kind) {
        return switch (kind) {
            case "create_case" -> change(kind,
                    "创建测试用例（单条或多条）。projectId 必填；多条时用 params.cases（每条必带 title，"
                            + "至多 20 条），单条可用 targetTitle + changes。调用仅生成变更预览，"
                            + "落库在用户确认之后。",
                    List.of(targetTitle("创建类按用户语义拟定"), projectIdProp()),
                    List.of("targetTitle", "projectId"),
                    params(List.of(
                            ToolSchemas.array("cases",
                                    "多条创建数据 [{title, priority, precondition, steps, expected}]",
                                    ToolSchemas.object(List.of(
                                            ToolSchemas.string("title", "用例标题"),
                                            ToolSchemas.string("priority", "优先级"),
                                            ToolSchemas.string("precondition", "前置条件"),
                                            ToolSchemas.stringArray("steps", "操作步骤", 50),
                                            ToolSchemas.stringArray("expected", "预期结果", 50)),
                                            List.of("title")), 20),
                            ToolSchemas.string("documentId", "落点文档 ID（取自 query_doc_tree）"),
                            ToolSchemas.string("parentId", "父节点 ID（取自 query_doc_tree，缺省挂根）")),
                            null));
            case "update_case", "complete_case" -> change(kind,
                    "修改 / 补全既有用例。params.caseIds 必填（取自 query_cases），changes 给字段级变更。"
                            + "调用仅生成变更预览，落库在用户确认之后。",
                    List.of(targetTitle("受影响用例的概括名"), changesProp()),
                    List.of("targetTitle", "changes"),
                    params(List.of(caseIdsProp()), List.of("caseIds")));
            case "batch_tag" -> change(kind,
                    "批量设置用例优先级 / 类型标记。params.caseIds 必填（取自 query_cases），"
                            + "changes 给 priority 或 type 变更。调用仅生成变更预览。",
                    List.of(targetTitle("受影响用例的概括名"), changesProp()),
                    List.of("targetTitle", "changes"),
                    params(List.of(caseIdsProp()), List.of("caseIds")));
            case "create_review" -> change(kind,
                    "创建测试评审并圈选用例。projectId 必填，params.caseIds 必填（圈选用例，取自 "
                            + "query_cases），可选 participantIds / description。调用仅生成变更预览。",
                    List.of(targetTitle("创建类按用户语义拟定"), projectIdProp()),
                    List.of("targetTitle", "projectId"),
                    params(List.of(
                            caseIdsProp(),
                            ToolSchemas.stringArray("participantIds", "参与者用户 ID 列表，缺省执行人本人", 50),
                            ToolSchemas.string("description", "评审说明")),
                            List.of("caseIds")));
            case "adjust_review" -> change(kind,
                    "调整既有评审的圈选用例。targetId 必填（取自 query_reviews），params.caseIds 必填。"
                            + "调用仅生成变更预览。",
                    List.of(targetIdProp("取自 query_reviews"), targetTitle("既有对象名称，可省略")),
                    List.of("targetId"),
                    params(List.of(caseIdsProp()), List.of("caseIds")));
            case "create_plan" -> change(kind,
                    "创建测试计划并圈选用例。projectId 必填，params.caseIds 必填，可选 description / "
                            + "environment / executorId / startTime / endTime（ISO 时间）。"
                            + "调用仅生成变更预览。",
                    List.of(targetTitle("创建类按用户语义拟定"), projectIdProp()),
                    List.of("targetTitle", "projectId"),
                    params(List.of(
                            caseIdsProp(),
                            ToolSchemas.string("description", "计划说明"),
                            ToolSchemas.string("environment", "测试环境"),
                            ToolSchemas.string("executorId", "执行人用户 ID"),
                            ToolSchemas.string("startTime", "开始时间（ISO-8601）"),
                            ToolSchemas.string("endTime", "结束时间（ISO-8601）")),
                            List.of("caseIds")));
            case "adjust_plan" -> change(kind,
                    "调整既有计划的圈选用例。targetId 必填（取自 query_plans），params.caseIds 必填。"
                            + "调用仅生成变更预览。",
                    List.of(targetIdProp("取自 query_plans"), targetTitle("既有对象名称，可省略")),
                    List.of("targetId"),
                    params(List.of(caseIdsProp()), List.of("caseIds")));
            default -> throw new IllegalStateException("unknown change kind: " + kind);
        };
    }

    private static AssistantToolDefinition change(String kind, String description,
            List<ToolSchemas.Prop> props, List<String> required, ToolSchemas.Prop paramsProp) {
        props = new java.util.ArrayList<>(props);
        props.add(paramsProp);
        return new AssistantToolDefinition(kind, description,
                ToolSchemas.object(props, required), ToolCategory.CHANGE);
    }

    private static ToolSchemas.Prop targetTitle(String hint) {
        return ToolSchemas.string("targetTitle", "目标名称：" + hint);
    }

    private static ToolSchemas.Prop targetIdProp(String hint) {
        return ToolSchemas.string("targetId", "既有对象 ID（" + hint + "，不得虚构）");
    }

    private static ToolSchemas.Prop projectIdProp() {
        return ToolSchemas.string("projectId", "目标项目 ID（取自 query_projects，必填）");
    }

    private static ToolSchemas.Prop changesProp() {
        return ToolSchemas.array("changes",
                "字段级变更 [{field, op: add|replace, value}]；字段限 title / priority / type / "
                        + "precondition / steps / expected，越界字段将被忽略",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("field", "字段名"),
                        ToolSchemas.string("op", "操作", List.of("add", "replace")),
                        ToolSchemas.string("value", "字段值（steps / expected 传字符串数组）")), null),
                30);
    }

    private static ToolSchemas.Prop caseIdsProp() {
        return ToolSchemas.stringArray("caseIds", "圈选 / 引用的用例 ID 列表（取自 query_cases，不得虚构）",
                200);
    }

    private static ToolSchemas.Prop params(List<ToolSchemas.Prop> props, List<String> required) {
        return new ToolSchemas.Prop("params", ToolSchemas.object(props, required));
    }
}
