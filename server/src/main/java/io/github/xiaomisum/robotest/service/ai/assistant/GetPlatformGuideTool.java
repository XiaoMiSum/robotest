package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.service.ai.assistant.GuideKnowledgeBase.GuideFragment;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * get_platform_guide：平台使用指引知识片段检索（详细设计 4.5）。
 *
 * <p>按当前用户空间角色过滤 + topic 关键词匹配取 Top 3；无命中返回空，
 * 由 system 指令约束 LLM 明确答复"超出使用指引范围"。</p>
 */
@Component
public class GetPlatformGuideTool implements AiTool {

    private static final String TOOL_NAME = "get_platform_guide";

    @Resource
    private GuideKnowledgeBase guideKnowledgeBase;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "检索平台功能使用指引（如「如何发起评审」、「如何创建测试计划」、「缺陷状态流转规则」、「如何编辑用例脑图」、「如何管理空间成员」）。返回匹配的知识片段，LLM 基于片段作答并附跳转路由。",
                ToolSchema.object(List.of(
                        ToolSchema.string("topic", "用户咨询的平台功能主题词，必填")),
                        List.of("topic")),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String topic = args.get("topic") instanceof String s ? s.trim() : "";
        if (topic.isEmpty()) {
            return toEmptyResult();
        }
        List<GuideKnowledgeBase.GuideFragment> matches =
                guideKnowledgeBase.search(context.userId(), context.workspaceId(), topic);
        if (matches.isEmpty()) {
            return toEmptyResult();
        }
        List<Map<String, Object>> fragments = new java.util.ArrayList<>();
        for (GuideKnowledgeBase.GuideFragment fragment : matches) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("topics", fragment.topics());
            item.put("route", fragment.route());
            item.put("content", fragment.content());
            fragments.add(item);
        }
        return JsonUtils.toJsonString(Map.of("count", fragments.size(), "fragments", fragments));
    }

    private String toEmptyResult() {
        return "{}";
    }
}
