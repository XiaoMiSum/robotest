package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiChatModelService;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * translate_minder_command：将用户自然语言编辑意图翻译为脑图 DSL 指令序列（全局智能助手 4.3）。
 *
 * <p>在 assistant_chat 的 Function Calling 循环内以只读工具执行：复用 dsl_translation 提示词模板
 * 与文档骨架上下文做一次同步 LLM 翻译，结果经 minder_commands 帧交前端复用 DSL 执行链路预览确认。
 * 审计与限流仅按 assistant_chat 记一次，此处不另计 dsl_translation。</p>
 */
@Service
@RequiredArgsConstructor
public class AiMinderTranslationTool implements AiTool {

    private static final String TOOL_NAME = "translate_minder_command";

    /** 骨架单行条数上限：全量节点列表会击穿 PromptAssembler 输入预算（4.4.1 不传全量节点、控制 token） */
    private static final int SKELETON_NODE_LIMIT = 300;

    /** DSL 动作类型 → 中文标签：回填 LLM 的 summary 仅含条数与类型清单，不携带全量 commands（4.3 控制 token） */
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "add_child", "新增节点",
            "mark_type", "标记类型",
            "mark_priority", "标记优先级",
            "move", "移动节点",
            "highlight", "高亮",
            "unknown", "未知操作");

    /** 翻译为确定性任务，低温度压低指令结构漂移 */
    private static final double TRANSLATION_TEMPERATURE = 0.1;

    /** 翻译任务指令固定为系统侧文本；用户指令放入业务数据由 PromptAssembler 定界，防注入（4.4.1 结构） */
    private static final String TASK_INSTRUCTION = """
            请将业务数据中的用户编辑意图翻译为脑图 DSL 指令序列，仅使用受支持的指令集，不执行超出用户意图的操作。
            每条指令结构：selector（types/priorities/keyword/subtreeRootTitle/aiGenerated，各条件为 AND 关系）
            + action（mark_type/mark_priority/highlight/move/add_child）。
            节点引用规则：仅允许引用输入骨架上下文中的节点标题（标题精确匹配），
            或使用保留值 @selected 表示当前选中节点；不得虚构上下文之外的节点标题。
            输出必须为 JSON 对象，结构必须严格遵循如下示例（字段名、类型、层级完全一致）：\
            {"commands": [{"selector": {"types": ["case"], "keyword": "登录"}, "action": {"type": "mark_priority", "params": {"priority": "P1"}}}], "ambiguous": false, "clarification": null}；\
            commands 按序执行，上限 10 条；翻译意图不明确时 ambiguous 置 true 并在 clarification 说明改写原因，commands 为空。""";

    private final PromptAssembler promptAssembler;
    private final OpenAiCompatProvider provider;
    private final AiChatModelService chatModelService;
    private final AiConfigService configService;
    private final TestCaseDocumentMapper testCaseDocumentMapper;
    private final TestCaseNodeMapper testCaseNodeMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "将用户的自然语言编辑意图翻译为脑图 DSL 指令序列。当用户希望在脑图中新增、修改、移动、删除节点时调用此工具。",
                ToolSchema.object(List.of(
                        ToolSchema.string("instruction", "用户的自然语言编辑指令，必填")),
                        List.of("instruction")),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String instruction = args.get("instruction") instanceof String s ? s.trim() : "";
        if (instruction.isEmpty()) {
            return errorResult("缺少 instruction 参数");
        }
        // documentId 仅由脑图编辑页的 pageContext 注入；无文档上下文时引导用户回脑图页使用（4.3）
        UUID documentId = documentIdFromContext(context);
        if (documentId == null) {
            return errorResult("缺少文档上下文 documentId，请在脑图编辑页使用该能力");
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            return errorResult("文档不存在或已被删除");
        }
        // 翻译作为 assistant_chat 会话内的一次工具调用，走同一开关与默认模型，失败按错误结果回填 LLM 转述
        if (!Boolean.TRUE.equals(configService.getStatus().getEnabled())) {
            return errorResult(ErrorCodeConstants.AI_NOT_ENABLED.msg());
        }
        ResolvedChatModel model = chatModelService.resolve(null);
        if (model == null) {
            return errorResult(ErrorCodeConstants.AI_NOT_ENABLED.msg());
        }

        String businessData = "【文档骨架上下文】\n" + buildSkeleton(document, documentId)
                + "\n【用户指令】\n" + instruction;
        List<AiModels.ChatMessage> messages = promptAssembler.assemble(
                AiFunctionType.DSL_TRANSLATION, TASK_INSTRUCTION, businessData);
        // 同步低温度翻译：结构化输出走 response_format json_object，确保命令可解析
        AiModels.ChatResult response = provider.complete(model, messages,
                new AiModels.ChatCallOptions(null, TRANSLATION_TEMPERATURE, true, null));
        return buildResult(response.content(), documentId);
    }

    /** 文档骨架上下文：名称/节点数 + 扁平的 id|标题|类型|父节点 清单，超限截断以控制 token */
    private String buildSkeleton(TestCaseDocument document, UUID documentId) {
        List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(documentId);
        StringBuilder skeleton = new StringBuilder();
        skeleton.append("【文档名称】").append(document.getName()).append('\n');
        skeleton.append("【节点总数】").append(nodes.size()).append('\n');
        skeleton.append("【树结构摘要】\n");
        int shown = Math.min(nodes.size(), SKELETON_NODE_LIMIT);
        for (int i = 0; i < shown; i++) {
            TestCaseNode node = nodes.get(i);
            skeleton.append("- ").append(node.getId())
                    .append(" | ").append(node.getTitle())
                    .append(" | type=").append(node.getType())
                    .append(" | parentId=").append(node.getParentId() != null ? node.getParentId() : "root")
                    .append('\n');
        }
        if (shown < nodes.size()) {
            skeleton.append("（节点过多，仅列出前 ").append(shown).append(" 条）\n");
        }
        return skeleton.toString();
    }

    /**
     * 解析并校验翻译结果：commands 非空时生成动作类型统计 summary，
     * 成功返回 {summary, documentId, commands}，失败返回错误结果供 LLM 转述引导改写。
     */
    private String buildResult(String content, UUID documentId) {
        String cleaned = AiOutputValidator.stripNoise(content);
        if (cleaned == null || cleaned.isEmpty()) {
            return errorResult("脑图指令翻译未返回有效内容，请重试");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            return errorResult("脑图指令翻译结果解析失败：" + e.getMessage());
        }
        JsonNode commands = root.get("commands");
        if (commands == null || !commands.isArray() || commands.isEmpty()) {
            // LLM 声明歧义时透出其澄清说明，引导用户改写而非盲目重试
            JsonNode clarification = root.get("clarification");
            if (clarification != null && clarification.isTextual() && !clarification.asText().isBlank()) {
                return errorResult(clarification.asText());
            }
            return errorResult("未能生成有效的脑图指令，请尝试更具体的描述");
        }

        Map<String, Long> actionCounts = new LinkedHashMap<>();
        for (JsonNode cmd : commands) {
            actionCounts.merge(actionType(cmd), 1L, Long::sum);
        }
        String summary = "已生成 " + commands.size() + " 条脑图操作指令：" + actionCounts.entrySet().stream()
                .map(entry -> ACTION_LABELS.getOrDefault(entry.getKey(), entry.getKey()) + " ×" + entry.getValue())
                .collect(Collectors.joining("、"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("documentId", documentId.toString());
        result.put("commands", objectMapper.convertValue(commands, List.class));
        return JsonUtils.toJsonString(result);
    }

    /** DSL action 可能为字符串（历史格式）或 {type, params} 对象（4.4.1 结构），统一取动作类型 */
    private String actionType(JsonNode cmd) {
        JsonNode action = cmd.get("action");
        if (action == null) {
            return "unknown";
        }
        if (action.isTextual()) {
            return action.asText();
        }
        if (action.isObject()) {
            JsonNode type = action.get("type");
            return type != null && type.isTextual() ? type.asText() : "unknown";
        }
        return "unknown";
    }

    /** pageContext 中的 documentId 兜底（与 WriteToolExecutor.projectIdFromContext 同款） */
    private UUID documentIdFromContext(AiToolContext context) {
        if (context.pageContext() == null) {
            return null;
        }
        Object val = context.pageContext().get("documentId");
        if (!(val instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String errorResult(String message) {
        return JsonUtils.toJsonString(Map.of("error", message));
    }
}
