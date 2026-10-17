package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantIntentBuilder;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantTool;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolContext;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolDefinition;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolRegistry;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工具调用循环编排（详设 4.1）：单条消息最多 5 跳，查询类即时执行并以 role=tool 消息回填，
 * 澄清 / 变更类终止本轮；tool_call 进度帧与逐跳用量由本类负责，终态统一以 {@link LoopOutcome}
 * 交回处理器——任何终态先落盘再发事件（详设 3.5）。
 *
 * <p>assistant(tool_calls) 轮次在内存中随循环推进（OpenAI 协议要求与 tool 回填成对出现），
 * 不落库；仅查询结果落 role=tool 消息供后续消息的多轮上下文使用（详设 4.4）。</p>
 */
@Component
public class AssistantToolLoop {

    private static final Logger log = LoggerFactory.getLogger(AssistantToolLoop.class);

    /** 轮次上限与固定文案（详设 4.1：达上限 done 终止，正常终止非错误码） */
    static final int MAX_LOOPS = 5;
    static final String LIMIT_TEXT = "已达到本轮工具调用上限，任务未完成，请简化请求后重试。";

    private static final String EVENT_TOOL_CALL = "tool_call";
    private static final String KIND_CLARIFY = "clarify";
    private static final String KIND_INTENT = "intent";
    private static final String KIND_ANSWER = "answer";
    private static final String SEARCH_KNOWLEDGE = "search_knowledge";

    @Resource
    private AssistantToolRegistry registry;
    @Resource
    private AssistantIntentBuilder intentBuilder;
    /** 回填消息写入下沉组件：依赖本组件而非会话服务，避免经注册表回到会话服务的循环依赖 */
    @Resource
    private AssistantToolMessageWriter toolMessageWriter;

    /**
     * 循环入参：提示词由处理器按场景渲染（自定义覆盖，详设 3.5），media 为当前消息附图直传字节
     * （详设 4.1 随行媒体口径），channel 可空（任务中心直提无通道）。
     */
    public record LoopRequest(UUID userId, UUID workspaceId, UUID conversationId,
            String systemPrompt, String userPrompt, List<AiChatMedia> media, AiModelConfig model,
            List<Map<String, Object>> attachments, AssistantStreamBridge.StreamChannel channel) {

        public LoopRequest {
            media = media == null ? List.of() : media;
            attachments = attachments == null ? List.of() : attachments;
        }
    }

    /**
     * 循环终态（kind ∈ clarify / intent / answer）：toolCalls 为本条消息累计的模型调用载荷
     * （终态消息落盘的审计轨迹，详设 2 消息表）；citations 仅 answer 终态可能非空（详设 4.1）。
     */
    public record LoopOutcome(String kind, String question, List<String> options,
            Map<String, Object> intent, String answer, List<Map<String, Object>> citations,
            List<Map<String, Object>> toolCalls, int tokensIn, int tokensOut) {

        public static LoopOutcome clarify(String question, List<String> options,
                List<Map<String, Object>> toolCalls, int tokensIn, int tokensOut) {
            return new LoopOutcome(KIND_CLARIFY, question, List.copyOf(options), null, null,
                    List.of(), List.copyOf(toolCalls), tokensIn, tokensOut);
        }

        public static LoopOutcome intent(Map<String, Object> intent, List<Map<String, Object>> toolCalls,
                int tokensIn, int tokensOut) {
            return new LoopOutcome(KIND_INTENT, null, List.of(), intent, null,
                    List.of(), List.copyOf(toolCalls), tokensIn, tokensOut);
        }

        public static LoopOutcome answer(String answer, List<Map<String, Object>> citations,
                List<Map<String, Object>> toolCalls, int tokensIn, int tokensOut) {
            return new LoopOutcome(KIND_ANSWER, null, List.of(), null, answer,
                    List.copyOf(citations), List.copyOf(toolCalls), tokensIn, tokensOut);
        }
    }

    public LoopOutcome run(LoopRequest request, AiModelClient modelClient) {
        List<AssistantToolDefinition> definitions = List.copyOf(registry.all());
        AssistantToolContext toolContext = new AssistantToolContext(
                request.userId(), request.workspaceId(), request.attachments());
        List<Message> turns = new ArrayList<>();
        List<Map<String, Object>> citations = new ArrayList<>();
        Set<String> citationKeys = new HashSet<>();
        List<Map<String, Object>> callTrail = new ArrayList<>();
        int tokensIn = 0;
        int tokensOut = 0;

        hops:
        for (int hop = 0; hop < MAX_LOOPS; hop++) {
            AiChatRequest chatRequest = new AiChatRequest(null, null, request.userId(), "assistant_parse",
                    request.model(), request.systemPrompt(), request.userPrompt(), request.media());
            AiChatReply reply = modelClient.chatWithTools(chatRequest, definitions, turns);
            tokensIn += reply.tokensIn();
            tokensOut += reply.tokensOut();

            if (reply.toolCalls().isEmpty()) {
                if (ModelOutputSupport.trimToNull(reply.content()) == null) {
                    // 纯文本跳无正文：模型输出不可用，按意图解析失败处理（255）
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
                }
                return LoopOutcome.answer(reply.content(), citations, callTrail, tokensIn, tokensOut);
            }
            // 本跳正文 + tool_calls 作为 assistant 轮次入列（下一次请求的协议前置）
            turns.add(assistantTurn(reply));
            for (AiToolCall call : reply.toolCalls()) {
                callTrail.add(callTrailEntry(call));
                AssistantToolDefinition definition = registry.definition(call.name());
                if (definition == null) {
                    // 清单外能力调用（详设 4.1）：不信赖模型，按意图解析失败拒绝
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
                }
                switch (definition.category()) {
                    case QUERY -> {
                        String result = queryResult(call, definition, toolContext, argsOrNull(call));
                        toolMessageWriter.appendToolMessage(request.conversationId(), call.id(), result);
                        sendToolCallFrame(request.channel(), call.name());
                        turns.add(toolTurn(call, result));
                        collectCitations(call.name(), result, citations, citationKeys);
                    }
                    case CLARIFY -> {
                        Map<String, Object> args;
                        try {
                            args = parseArgs(call);
                        } catch (ServiceException e) {
                            turns.add(toolTurn(call, errorJson("工具参数 JSON 非法")));
                            continue hops;
                        }
                        try {
                            registry.executable(call.name()).execute(toolContext, args);
                        } catch (RuntimeException invalid) {
                            // 澄清入参非法：回填错误让模型修正，不终止会话（详设 4.1）
                            turns.add(toolTurn(call, errorJson(invalid.getMessage())));
                            continue hops;
                        }
                        return LoopOutcome.clarify(
                                ModelOutputSupport.trimToNull(ModelOutputSupport.asString(args.get("question"))),
                                optionsOf(args.get("options")), callTrail, tokensIn, tokensOut);
                    }
                    case CHANGE -> {
                        // 变更类：入参规范化为固化 intent，确认前零业务写入（详设 4.1 / 4.2①）
                        Map<String, Object> intent = intentBuilder.normalize(
                                call.name(), parseArgs(call), request.workspaceId());
                        return LoopOutcome.intent(intent, callTrail, tokensIn, tokensOut);
                    }
                    default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
                }
            }
        }
        return LoopOutcome.answer(LIMIT_TEXT, List.of(), callTrail, tokensIn, tokensOut);
    }

    // ---------- 轮次构造 ----------

    private static Message assistantTurn(AiChatReply reply) {
        List<AssistantMessage.ToolCall> calls = reply.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
                .toList();
        return AssistantMessage.builder()
                .content(ModelOutputSupport.nvl(reply.content()))
                .toolCalls(calls)
                .build();
    }

    private static Message toolTurn(AiToolCall call, String result) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result)))
                .build();
    }

    private static Map<String, Object> callTrailEntry(AiToolCall call) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", call.id());
        entry.put("name", call.name());
        entry.put("arguments", ModelOutputSupport.abbreviate(call.arguments(), 4000));
        return entry;
    }

    // ---------- 工具执行与结果 ----------

    /** 查询参数宽松解析：JSON 非法时回填错误让模型修正（不中断会话），其余异常一律错误文本回填 */
    private String queryResult(AiToolCall call, AssistantToolDefinition definition,
            AssistantToolContext toolContext, Map<String, Object> args) {
        if (args == null) {
            return errorJson("工具参数 JSON 非法");
        }
        AssistantTool tool = registry.executable(call.name());
        if (tool == null) {
            return errorJson("工具不可执行: " + call.name());
        }
        try {
            return tool.execute(toolContext, args);
        } catch (ServiceException e) {
            return errorJson(e.getCode() + ": " + ModelOutputSupport.nvl(e.getMessage()));
        } catch (Exception e) {
            log.warn("[AI] 助手查询工具执行失败 tool={} category={}", definition.name(),
                    definition.category(), e);
            return errorJson("工具执行异常: " + ModelOutputSupport.nvl(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> argsOrNull(AiToolCall call) {
        try {
            return parseArgs(call);
        } catch (ServiceException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(AiToolCall call) {
        String arguments = ModelOutputSupport.trimToNull(call.arguments());
        if (arguments == null) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseObject(arguments, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
        }
    }

    private static List<String> optionsOf(Object raw) {
        List<String> options = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                String option = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(element));
                if (option != null) {
                    options.add(ModelOutputSupport.abbreviate(option, 200));
                }
            }
        }
        return options;
    }

    private static String errorJson(String message) {
        return JsonUtils.toJsonString(Map.of("error", ModelOutputSupport.abbreviate(
                ModelOutputSupport.nvl(message), 500)));
    }

    // ---------- 进度帧与引用 ----------

    private static void sendToolCallFrame(AssistantStreamBridge.StreamChannel channel, String toolName) {
        if (channel != null) {
            channel.send(EVENT_TOOL_CALL, Map.of("toolName", toolName, "summary", summarize(toolName)));
        }
    }

    private static String summarize(String toolName) {
        return switch (toolName) {
            case "query_projects" -> "查询项目";
            case "query_doc_tree" -> "查询文档树";
            case "query_cases" -> "检索用例";
            case "query_reviews" -> "查询评审";
            case "query_plans" -> "查询计划";
            case "query_progress" -> "查询进度";
            case SEARCH_KNOWLEDGE -> "检索知识";
            default -> "执行 " + toolName;
        };
    }

    /** 检索类工具结果聚引用（详设 4.1 citations）：hits 去重，供只读问答随终态消息落库 */
    private static void collectCitations(String toolName, String result,
            List<Map<String, Object>> citations, Set<String> keys) {
        if (!SEARCH_KNOWLEDGE.equals(toolName)) {
            return;
        }
        try {
            Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
            if (parsed == null || !(parsed.get("hits") instanceof List<?> hits)) {
                return;
            }
            for (Object element : hits) {
                if (!(element instanceof Map<?, ?> hit)) {
                    continue;
                }
                String key = hit.get("type") + "|" + hit.get("id");
                if (!keys.add(key)) {
                    continue;
                }
                Map<String, Object> citation = new LinkedHashMap<>();
                citation.put("type", hit.get("type"));
                citation.put("id", hit.get("id"));
                citation.put("title", hit.get("title"));
                citation.put("quote", hit.get("quote"));
                citations.add(citation);
            }
        } catch (Exception e) {
            // 引用为辅助信息，解析失败静默忽略（不阻断回答）
        }
    }
}
