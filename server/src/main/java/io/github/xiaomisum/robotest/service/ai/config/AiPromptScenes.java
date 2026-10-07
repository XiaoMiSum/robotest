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
        register(scenes, new PromptScene("requirement_split", "需求拆分", List.of(
                new PromptVariable("requirementCode", "需求编号", true),
                new PromptVariable("requirementTitle", "需求标题", true),
                new PromptVariable("requirementDescription", "需求描述正文", true),
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
        register(scenes, new PromptScene("coverage_analysis", "覆盖分析", List.of(
                new PromptVariable("requirementContext", "需求条目上下文（编号、标题与描述）", true),
                new PromptVariable("caseContext", "关联测试用例清单（id 与标题）", true))));
        SCENES = Collections.unmodifiableMap(scenes);
    }

    /**
     * 阶段子场景内置默认（生成链详设 3.3）：scene 不等于任务 type，处理器 defaultPrompt 不可达，
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
