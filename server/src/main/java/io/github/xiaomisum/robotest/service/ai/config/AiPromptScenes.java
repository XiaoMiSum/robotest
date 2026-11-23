package io.github.xiaomisum.robotest.service.ai.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景登记常量（详设 3.5「场景已登记」的判定依据）：scene → 场景名 + 可用变量清单。
 * 任务场景随对应 TypeAdapter 登记元数据；未登记 scene 的保存 / 查询报 1000018108。
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
        register(scenes, new PromptScene("assistant_parse", "助手任务执行", List.of(
                new PromptVariable("history", "会话近 20 条消息（时间正序，角色:内容，含工具结果）", true),
                new PromptVariable("context", "会话私有项目状态与页面上下文引用", true))));
        register(scenes, new PromptScene("bug_draft_suggest", "新建缺陷建议", List.of(
                new PromptVariable("bugContext", "缺陷草稿（标题 / 重现步骤 / 已选模块）", true),
                new PromptVariable("moduleOptions", "候选模块清单", true),
                new PromptVariable("caseOptions", "候选用例清单（id|标题，无则为空）", true))));
        register(scenes, new PromptScene("case_optimize", "用例优化", List.of(
                new PromptVariable("caseContext", "选中用例节点与现有属性清单", true),
                new PromptVariable("documentContext", "文档层级上下文", true),
                new PromptVariable("requirementContext", "上游需求上下文", true))));
        register(scenes, new PromptScene("mindmap_case_generation", "脑图生成用例", List.of(
                new PromptVariable("requirementContext", "来源需求冻结快照", true),
                new PromptVariable("documentContext", "当前文档与已存在用例标题", true),
                new PromptVariable("ragContext", "向量检索命中的需求片段（可为空）", false))));
        register(scenes, new PromptScene("plan_order", "执行顺序建议", List.of(
                new PromptVariable("planContext", "计划关联用例与最近执行结果", true),
                new PromptVariable("roundCount", "推荐轮次（缺省空串）", false))));
        register(scenes, new PromptScene("review_case_review", "AI 评审规划用例", List.of(
                new PromptVariable("reviewCaseContext", "关联规划用例快照与祖先路径", true),
                new PromptVariable("requirementContext", "关联需求冻结快照", true),
                new PromptVariable("ragContext", "向量检索命中的需求片段（可为空）", false))));
        SCENES = Collections.unmodifiableMap(scenes);
    }

    /**
     * 非任务场景内置默认；任务场景正文唯一来自 TypeAdapter。
     */
    private static final Map<String, String> STAGE_PROMPTS = Map.ofEntries();

    private AiPromptScenes() {
    }

    public static PromptScene get(String scene) {
        return SCENES.get(scene);
    }

    public static Collection<PromptScene> all() {
        return SCENES.values();
    }

    /** 非任务场景内置默认；任务场景返回 null。 */
    public static String builtin(String scene) {
        return STAGE_PROMPTS.get(scene);
    }

    private static void register(Map<String, PromptScene> scenes, PromptScene scene) {
        scenes.put(scene.scene(), scene);
    }
}
