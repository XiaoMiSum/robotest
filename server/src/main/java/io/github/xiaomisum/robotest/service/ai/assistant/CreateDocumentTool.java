package io.github.xiaomisum.robotest.service.ai.assistant;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * create_document 写工具定义（详细设计 4.1 / 4.3）。
 *
 * <p>仅向 LLM 提供工具定义（名称、描述、参数 Schema）；实际执行在用户确认后
 * 由 {@link WriteToolExecutor} 完成，此处的 execute 不会被调用。</p>
 */
@Service
public class CreateDocumentTool implements AiTool {

    private static final String TOOL_NAME = "create_document";

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "在项目中创建用例文档，可指定目标模块并同时在文档下创建初始用例节点。"
                        + "适用于用户要求新建或增加用例文档的场景。若目标模块不存在，可先调用 create_module 创建模块。",
                ToolSchema.object(List.of(
                        ToolSchema.string("projectId", "项目 ID，从页面上下文获取，必填"),
                        ToolSchema.string("moduleName", "目标模块名称，按名称在项目模块树中匹配，可选"),
                        ToolSchema.string("documentName", "文档名称，必填"),
                        ToolSchema.array("caseNodes", "初始用例节点数组，每项含 title 和 priority，可选",
                                ToolSchema.object(List.of(
                                        ToolSchema.string("title", "用例标题"),
                                        ToolSchema.string("priority", "用例优先级",
                                                List.of("P0", "P1", "P2", "P3"))),
                                        List.of("title")))),
                        List.of("projectId", "documentName")),
                false, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        // 写工具通过 WriteToolExecutor 在确认流程中执行，此处不会被调用
        return "{\"error\":\"写工具不应直接执行\"}";
    }
}
