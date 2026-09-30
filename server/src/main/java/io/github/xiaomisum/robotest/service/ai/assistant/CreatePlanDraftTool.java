package io.github.xiaomisum.robotest.service.ai.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * create_plan_draft 写工具定义（详细设计 4.1）。
 *
 * <p>仅向 LLM 提供工具定义（名称、描述、参数 Schema）；实际执行在用户确认后
 * 由 {@link WriteToolExecutor} 完成，此处的 execute 不会被调用。</p>
 */
@Service
public class CreatePlanDraftTool implements AiTool {

    private static final String TOOL_NAME = "create_plan_draft";

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "在项目中创建测试计划草稿（状态为 new）。适用于用户要求创建计划或计划草稿的场景。",
                ToolSchema.object(List.of(
                        ToolSchema.string("projectId", "项目 ID，从页面上下文获取，必填"),
                        ToolSchema.string("name", "计划名称，必填"),
                        ToolSchema.string("description", "计划描述，可选")),
                        List.of("projectId", "name")),
                false, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        // 写工具通过 WriteToolExecutor 在确认流程中执行，此处不会被调用
        return "{\"error\":\"写工具不应直接执行\"}";
    }
}
