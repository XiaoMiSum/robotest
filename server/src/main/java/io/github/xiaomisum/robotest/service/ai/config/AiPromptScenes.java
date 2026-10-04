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
        register(scenes, new PromptScene("requirement_split", "需求拆分", List.of(
                new PromptVariable("requirementCode", "需求编号", true),
                new PromptVariable("requirementTitle", "需求标题", true),
                new PromptVariable("requirementDescription", "需求描述正文", true),
                new PromptVariable("moduleOptions", "候选模块清单", true))));
        SCENES = Collections.unmodifiableMap(scenes);
    }

    private AiPromptScenes() {
    }

    public static PromptScene get(String scene) {
        return SCENES.get(scene);
    }

    public static Collection<PromptScene> all() {
        return SCENES.values();
    }

    private static void register(Map<String, PromptScene> scenes, PromptScene scene) {
        scenes.put(scene.scene(), scene);
    }
}
