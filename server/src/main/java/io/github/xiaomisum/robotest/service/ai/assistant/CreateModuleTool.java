package io.github.xiaomisum.robotest.service.ai.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * create_module 写工具定义（详细设计 4.1 / 4.3）。
 *
 * <p>仅向 LLM 提供工具定义（名称、描述、参数 Schema）；实际执行在用户确认后
 * 由 {@link WriteToolExecutor} 完成，此处的 execute 不会被调用。</p>
 */
@Service
public class CreateModuleTool implements AiTool {

    private static final String TOOL_NAME = "create_module";

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "在项目中创建模块，适用于目标模块尚不存在需先创建的场景。",
                ToolSchema.object(List.of(
                        ToolSchema.string("projectId", "项目 ID，从页面上下文获取，必填"),
                        ToolSchema.string("parentModuleName", "父模块名称，按名称在项目模块树中匹配，可选，不填则创建顶级模块"),
                        ToolSchema.string("moduleName", "模块名称，必填")),
                        List.of("projectId", "moduleName")),
                false, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        // 写工具通过 WriteToolExecutor 在确认流程中执行，此处不会被调用
        return "{\"error\":\"写工具不应直接执行\"}";
    }
}
