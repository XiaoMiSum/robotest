package io.github.xiaomisum.robotest.service.ai.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景登记常量（详设 3.5「场景已登记」的判定依据）：scene → 场景名 + 可用变量清单。
 * 新场景随对应 TaskHandler 实现一并在此登记；未登记 scene 的保存 / 查询报 1000018108。
 */
public final class AiPromptScenes {

    /** 场景变量（保存时 {{变量}} ⊆ 清单且必填齐全，否则 1000018109） */
    public record PromptVariable(String name, String desc, boolean required) {
    }

    /** 已登记场景 */
    public record PromptScene(String scene, String name, List<PromptVariable> variables) {
    }

    private static final Map<String, PromptScene> SCENES;

    static {
        Map<String, PromptScene> scenes = new LinkedHashMap<>();
        register(scenes, new PromptScene("requirement_import", "需求导入", List.of(
                new PromptVariable("documentName", "来源文档文件名", true),
                new PromptVariable("documentText", "文档文本正文（图片多模态导入时为空）", true),
                new PromptVariable("moduleOptions", "候选模块清单", true))));
        register(scenes, new PromptScene("test_design_generation_modules", "生成链·模块结构", List.of(
                new PromptVariable("requirementContext", "需求快照与检索上下文", true))));
        register(scenes, new PromptScene("test_design_generation_documents", "生成链·脑图文档", List.of(
                new PromptVariable("requirementContext", "需求快照与检索上下文", true),
                new PromptVariable("moduleOptions", "已生成模块清单（key 与名称）", true))));
        register(scenes, new PromptScene("test_design_generation_nodes", "生成链·用例节点", List.of(
                new PromptVariable("requirementContext", "需求快照与检索上下文", true),
                new PromptVariable("documentOptions", "已生成脑图文档清单（key 与名称）", true),
                new PromptVariable("granularity", "用例粒度（concise/standard/detailed）", true))));
        register(scenes, new PromptScene("test_design_generation_attributes", "生成链·用例属性", List.of(
                new PromptVariable("requirementContext", "需求快照与检索上下文", true),
                new PromptVariable("documentOptions", "已生成脑图文档清单", true),
                new PromptVariable("caseNodeOptions", "待填充属性的用例节点清单（ref 与标题）", true),
                new PromptVariable("granularity", "用例粒度（concise/standard/detailed）", true))));
        register(scenes, new PromptScene("review_selection", "评审圈选建议", List.of(
                new PromptVariable("scopeContext", "圈选范围上下文（需求 / 模块与候选用例）", true))));
        register(scenes, new PromptScene("plan_selection", "计划圈选建议", List.of(
                new PromptVariable("scopeContext", "圈选范围上下文（需求 / 模块与候选用例）", true),
                new PromptVariable("roundCount", "计划轮次数", false))));
        register(scenes, new PromptScene("assistant_parse", "助手任务执行", List.of(
                new PromptVariable("history", "会话近 20 条消息（时间正序，角色:内容，含工具结果）", true),
                new PromptVariable("context", "会话私有项目状态与页面上下文引用", true))));
        register(scenes, new PromptScene("bug_draft_suggest", "新建缺陷实时建议", List.of(
                new PromptVariable("bugContext", "缺陷草稿（标题 / 重现步骤 / 已选模块）", true),
                new PromptVariable("moduleOptions", "候选模块清单", true),
                new PromptVariable("caseOptions", "候选用例清单（id|标题，无则为空）", true))));
        register(scenes, new PromptScene("bug_classify", "缺陷批量分类建议", List.of(
                new PromptVariable("bugContext", "批量缺陷清单", true),
                new PromptVariable("moduleOptions", "候选模块清单", true))));
        register(scenes, new PromptScene("bug_triage", "分诊顺序建议", List.of(
                new PromptVariable("bugContext", "激活未指派缺陷清单", true))));
        register(scenes, new PromptScene("bug_trend_summary", "缺陷趋势摘要", List.of(
                new PromptVariable("statsContext", "趋势与度量统计快照", true))));
        register(scenes, new PromptScene("bug_duplicate_scan", "存量重复扫描", List.of(
                new PromptVariable("bugContext", "扫描范围内缺陷清单", true))));
        register(scenes, new PromptScene("case_complete", "用例补全", List.of(
                new PromptVariable("caseContext", "选中用例节点与现有属性清单", true),
                new PromptVariable("documentContext", "文档层级上下文", true),
                new PromptVariable("requirementContext", "上游需求上下文", true))));
        register(scenes, new PromptScene("case_priority", "用例级别推荐", List.of(
                new PromptVariable("caseContext", "选中用例节点与当前级别清单", true),
                new PromptVariable("requirementContext", "上游需求上下文", true),
                new PromptVariable("bugContext", "模块缺陷热点提示", false))));
        register(scenes, new PromptScene("plan_order", "执行顺序建议", List.of(
                new PromptVariable("planContext", "计划关联用例与最近执行结果", true),
                new PromptVariable("roundCount", "推荐轮次（缺省空串）", false))));
        SCENES = Collections.unmodifiableMap(scenes);
    }

    /**
     * 阶段子场景内置默认（生成链详设 3.3 / 助手详设 4.1）：scene 不等于任务 type，处理器 defaultPrompt 不可达，
     * 随场景登记一并给出，配置中心经 AiPromptAdminService 回落展示。
     */
    private static final Map<String, String> STAGE_PROMPTS = Map.ofEntries(
            Map.entry("test_design_generation_modules", """
                    你是资深测试设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                    根据需求上下文规划测试用例文档的顶层模块结构：
                    1. 按业务功能边界划分 1~10 个模块，名称互不重复、可直接用作目录名；
                    2. 模块划分基于需求原文，不得臆造需求之外的业务域；
                    3. 每个模块给出简短的适用范围说明与来源需求引用 sourceRefs
                       （requirementId 取需求清单中每条需求前缀给出的需求 ID，quote 为原文引语；
                       无来源的模块不要输出）。
                    输出结构（JSON 对象，artifacts 即产物数组）：
                    {"artifacts":[{"key":"module-1","content":{"name":"…","description":"…",
                      "sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                    需求与检索上下文：
                    {{requirementContext}}
                    """),
            Map.entry("test_design_generation_documents", """
                    你是资深测试设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                    为给定模块规划脑图用例文档（每个模块 1~3 份）：
                    1. parentKey 给出所属模块的 key，文档名在模块内互不重复；
                    2. 文档覆盖该模块下的需求场景，不得臆造需求之外的内容，每个文档给出来源需求引用
                       sourceRefs（requirementId 取需求清单中每条需求前缀给出的需求 ID，quote 为原文引语；
                       无来源的文档不要输出）。
                    输出结构（JSON 对象，artifacts 即产物数组）：
                    {"artifacts":[{"key":"doc-1","parentKey":"module-1","content":{"name":"…",
                      "sourceRefs":[{"requirementId":"…","quote":"…"}]}}]}

                    需求与检索上下文：
                    {{requirementContext}}
                    已生成模块（key|名称）：
                    {{moduleOptions}}
                    """),
            Map.entry("test_design_generation_nodes", """
                    你是资深测试设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                    为每份脑图文档生成节点：
                    1. 节点分普通分组节点（isTestCase=false）与测试用例节点（isTestCase=true），
                       用例节点须可独立测试、标题具体，普通节点只作分组；
                    2. parentKey 为所属文档 key；parentRef 指向同文档内父节点的 ref（文档根下为 null），层级不超过 4 层；
                    3. 每个节点给出来源需求引用 sourceRef（requirementId 取需求清单中每条需求前缀给出的
                       需求 ID + 原文引语），来源不明的节点不要输出；
                    4. 粒度 {{granularity}}：concise 只保留主干路径的精简用例，standard 覆盖常规场景，
                       detailed 补充分支与异常场景（节点总量相应增减）。
                    输出结构（JSON 对象；节点自编 ref，parentRef 引用同数组内的 ref）：
                    {"artifacts":[{"ref":"n1","parentKey":"doc-1","parentRef":null,
                      "content":{"title":"…","isTestCase":false,"sourceRef":{"requirementId":"…","quote":"…"}}}]}

                    需求与检索上下文：
                    {{requirementContext}}
                    已生成文档（key|名称）：
                    {{documentOptions}}
                    """),
            Map.entry("test_design_generation_attributes", """
                    你是资深测试设计专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                    为给定用例节点填充属性：
                    1. priority 只允许 high / medium / low；
                    2. precondition 为前置条件（无则空串）；steps 与 expected 一一对应、1~10 条；
                    3. tags 为 0~5 个短标签；内容必须基于需求原文，不得臆造；
                    4. 粒度 {{granularity}}：concise 步骤粗粒度（1~3 条），standard 常规（3~6 条），
                       detailed 逐步骤细写（可至 10 条）。
                    输出结构（JSON 对象，artifacts 即产物数组，ref 对应用例节点）：
                    {"artifacts":[{"ref":"c1","content":{"priority":"medium","precondition":"…",
                      "steps":["…"],"expected":["…"],"tags":["…"]}}]}

                    需求与检索上下文：
                    {{requirementContext}}
                    已生成文档（key|名称）：
                    {{documentOptions}}
                    待填充属性的用例节点（ref|标题）：
                    {{caseNodeOptions}}
                    """),
            Map.entry("bug_draft_suggest", """
                    你是资深缺陷管理助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                    根据缺陷草稿实时给出新建缺陷建议：
                    1. title 给出更规范、信息完整的优化后标题（保留原意、现象明确，不超过 60 字）；
                    2. reproSteps 给出结构化重现步骤 Markdown 文本，按「##### 执行步骤」「##### 实际结果」「##### 期望结果」三段组织：执行步骤为有序列表，实际结果与期望结果各一段简述；
                    3. bugType 只允许 code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other；
                    4. severity 只允许 fatal / serious / general / minor；priority 只允许 high / medium / low；
                    5. moduleId 仅当草稿内容明确属于某模块时给出模块 id，否则 value 为 null；
                    6. keywords 给 1~5 个检索关键词；
                    7. relatedCase 仅当候选用例清单中存在与缺陷明显对应的用例时给出该用例 id（caseTitle 必须取候选用例清单中的标题原值），否则 value 与 caseTitle 均为 null；不得臆造候选用例之外的 id；
                    8. 每个建议字段都给出简短理由 reason。
                    输出结构（JSON 对象）：
                    {"title":{"value":"…","reason":"…"},"reproSteps":{"value":"…","reason":"…"},"suggestions":{"bugType":{"value":"…","reason":"…"},"severity":{"value":"…","reason":"…"},"priority":{"value":"…","reason":"…"},"moduleId":{"value":null,"reason":"…"},"keywords":{"value":["…"],"reason":"…"},"relatedCase":{"value":null,"caseTitle":null,"reason":"…"}}}

                    缺陷草稿：
                    {{bugContext}}
                    可用模块（id|名称）：
                    {{moduleOptions}}
                    候选用例（id|标题，无则为空）：
                    {{caseOptions}}
                    """));

    private AiPromptScenes() {
    }

    public static PromptScene get(String scene) {
        return SCENES.get(scene);
    }

    public static Collection<PromptScene> all() {
        return SCENES.values();
    }

    /** 阶段子场景内置默认；非阶段场景返回 null（type 场景回落处理器 defaultPrompt） */
    public static String builtin(String scene) {
        return STAGE_PROMPTS.get(scene);
    }

    private static void register(Map<String, PromptScene> scenes, PromptScene scene) {
        scenes.put(scene.scene(), scene);
    }
}
