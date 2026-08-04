package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantSendReqDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiMessage;
import io.github.xiaomisum.robotest.repository.ai.AiConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiMessageMapper;
import io.github.xiaomisum.robotest.service.ai.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatMessage;
import io.github.xiaomisum.robotest.service.ai.AiModels.ToolCall;
import io.github.xiaomisum.robotest.service.ai.AiModels.ToolDefinition;
import io.github.xiaomisum.robotest.service.ai.AiModels.ToolStreamCallbacks;
import io.github.xiaomisum.robotest.service.ai.assistant.AiConfirmTokenService;
import io.github.xiaomisum.robotest.service.ai.assistant.AiTool;
import io.github.xiaomisum.robotest.service.ai.assistant.AiToolContext;
import io.github.xiaomisum.robotest.service.ai.assistant.AiToolDefinition;
import io.github.xiaomisum.robotest.service.ai.assistant.AiToolExecutor;
import io.github.xiaomisum.robotest.service.ai.assistant.ToolRegistry;
import io.github.xiaomisum.robotest.service.ai.assistant.WriteToolExecutor;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局智能助手对话服务实现（详细设计 3.2 / 3.3 / 4.2）。
 *
 * <p>Function Calling 执行循环（上限 5 次/轮）+ 消息落库 + 标题自动更名 +
 * 悬空补偿（超时未消费的 tool_calls 自动补齐 tool 消息）+ SSE 帧发送。</p>
 */
@Slf4j
@Service
public class AiAssistantChatServiceImpl implements AiAssistantChatService {

    private static final long SSE_TIMEOUT_MILLIS = 120_000L;
    private static final long PING_INTERVAL_SECONDS = 15L;
    private static final int MAX_FUNCTION_CALL_LOOPS = 5;
    private static final int CONTEXT_HISTORY_ROUNDS = 10;

    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ai-assistant-ping");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private AiConversationMapper conversationMapper;
    @Resource
    private AiMessageMapper messageMapper;
    @Resource
    private AiConversationService conversationService;
    @Resource
    private AiChatModelService aiChatModelService;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private AiRateLimiter rateLimiter;
    @Resource
    private AiAuditRecorder auditRecorder;
    @Resource
    private OpenAiCompatProvider provider;
    @Resource
    private PromptAssembler promptAssembler;
    @Resource
    private ToolRegistry toolRegistry;
    @Resource
    private AiToolExecutor aiToolExecutor;
    @Resource
    private AiConfirmTokenService confirmTokenService;
    @Resource
    private WriteToolExecutor writeToolExecutor;

    @Override
    public SseEmitter sendMessage(UUID userId, UUID workspaceId, UUID conversationId,
                                  AiAssistantSendReqDTO reqDTO) {
        // 1. 校验会话归属
        AiConversation conversation = requireOwned(userId, workspaceId, conversationId);
        // 2. 落库用户消息 + 自动更名 + 触碰 lastActiveAt
        conversationService.appendUserMessage(conversationId, reqDTO.getContent());
        // 3. 限流检查（按 assistant_chat 记一次）
        ResolvedChatModel resolvedModel = requireChatModel(userId, workspaceId, reqDTO.getModelId());
        UUID parsedModelId = null;
        if (reqDTO.getModelId() != null && !reqDTO.getModelId().isBlank()) {
            try { parsedModelId = UUID.fromString(reqDTO.getModelId()); } catch (IllegalArgumentException ignored) { }
        }
        AiCallContext callContext = new AiCallContext(userId, workspaceId, null, parsedModelId);
        rateLimiter.checkAndRecord(userId, AiFunctionType.ASSISTANT_CHAT);

        // 4. 构建上下文 + 启动 SSE 流
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> { cancelled.set(true); emitter.complete(); });

        ScheduledFuture<?> ping = pingScheduler.scheduleAtFixedRate(() -> {
            try { emitter.send(SseEmitter.event().comment("ping")); }
            catch (Exception e) { cancelled.set(true); }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Thread.startVirtualThread(() -> {
            try {
                List<ChatMessage> contextMessages = buildContextMessages(conversationId, reqDTO.getContent());
                List<ToolDefinition> toolDefs = buildToolDefinitions(userId, workspaceId);
                ChatCallOptions options = toolDefs.isEmpty()
                        ? ChatCallOptions.defaults()
                        : ChatCallOptions.withTools(toolDefs);
                long start = System.currentTimeMillis();
                int loopCount = 0;
                while (loopCount < MAX_FUNCTION_CALL_LOOPS) {
                    if (cancelled.get()) { break; }
                    // 检查 AI 总开关
                    if (!Boolean.TRUE.equals(aiConfigService.getStatus().getEnabled())) {
                        sendError(emitter, ErrorCodeConstants.AI_NOT_ENABLED.code(),
                                ErrorCodeConstants.AI_NOT_ENABLED.msg());
                        break;
                    }
                    // 调用 LLM（流式）
                    List<ToolCall>[] toolCallsHolder = new List[]{List.of()};
                    StringBuilder fullContent = new StringBuilder();
                    provider.streamWithTools(resolvedModel, contextMessages, options,
                            new ToolStreamCallbacks() {
                                @Override
                                public void onDelta(String content) {
                                    if (!cancelled.get()) {
                                        send(emitter, "delta", Map.of("content", content));
                                    }
                                }
                                @Override
                                public void onToolCalls(List<ToolCall> toolCalls) {
                                    toolCallsHolder[0] = toolCalls;
                                }
                                @Override
                                public void onFinish(String fc, Integer pt, Integer ct) {
                                    fullContent.setLength(0);
                                    fullContent.append(fc);
                                    auditRecorder.record(callContext, AiFunctionType.ASSISTANT_CHAT,
                                            resolvedModel.model(), System.currentTimeMillis() - start,
                                            pt, ct, Constants.AiInvocationStatus.SUCCESS, null);
                                }
                            }, cancelled);
                    List<ToolCall> toolCalls = toolCallsHolder[0];
                    // 无工具调用 → 纯文本回复，落库 + done 帧
                    if (toolCalls.isEmpty()) {
                        UUID msgId = conversationService.appendAssistantMessage(
                                conversationId, fullContent.toString(), null);
                        send(emitter, "done", Map.of("messageId", msgId.toString()));
                        emitter.complete();
                        ping.cancel(false);
                        return;
                    }
                    // 有工具调用 → 落库 assistant 消息（含 tool_calls 载荷）
                    UUID assistantMsgId = conversationService.appendAssistantMessage(
                            conversationId, fullContent.toString(), toolCalls);
                    // 串行执行工具
                    boolean writeToolEncountered = false;
                    String confirmTokenForWrite = null;
                    ToolCall writeToolCall = null;
                    for (ToolCall tc : toolCalls) {
                        if (cancelled.get()) { break; }
                        AiTool tool = toolRegistry.get(tc.name());
                        if (tool == null) {
                            // 未知工具 → 落 tool 消息
                            String errResult = "{\"error\":\"未知工具: " + tc.name() + "\"}";
                            conversationService.appendToolMessage(conversationId, tc.id(), errResult);
                            send(emitter, "tool_call", Map.of("toolName", tc.name(), "summary", "未知工具"));
                            continue;
                        }
                        if (!tool.definition().readOnly()) {
                            // 写工具 → 生成确认令牌，中断本轮循环
                            writeToolEncountered = true;
                            writeToolCall = tc;
                            AiToolContext toolCtx = new AiToolContext(userId, workspaceId, reqDTO.getPageContext());
                            confirmTokenForWrite = confirmTokenService.issue(
                                    userId, workspaceId, conversationId,
                                    assistantMsgId, tc.id(), tc.name(), tc.arguments());
                            break;
                        }
                        // 只读工具 → 直接执行
                        AiToolContext toolCtx = new AiToolContext(userId, workspaceId, reqDTO.getPageContext());
                        String result = aiToolExecutor.execute(toolCtx, tc.name(), tc.arguments());
                        conversationService.appendToolMessage(conversationId, tc.id(), result);
                        send(emitter, "tool_call", Map.of(
                                "toolName", tc.name(),
                                "summary", summarizeToolCall(tc.name(), tc.arguments())));
                    }
                    if (writeToolEncountered && confirmTokenForWrite != null) {
                        // 写工具中断 → confirm_required 帧 + done
                        Map<String, Object> confirmData = new LinkedHashMap<>();
                        confirmData.put("confirmToken", confirmTokenForWrite);
                        confirmData.put("toolName", writeToolCall.name());
                        confirmData.put("preview", writeToolCall.arguments());
                        confirmData.put("expiresAt", Instant.now()
                                .plusSeconds(aiConfigService.getIntSetting("assistantConfirmTimeoutSeconds"))
                                .toString());
                        send(emitter, "confirm_required", confirmData);
                        send(emitter, "done", Map.of("messageId", assistantMsgId.toString()));
                        emitter.complete();
                        ping.cancel(false);
                        return;
                    }
                    // 将本轮 assistant+tool 消息追加到上下文，继续循环
                    contextMessages.add(ChatMessage.assistant(fullContent.toString(), toolCalls));
                    for (ToolCall tc : toolCalls) {
                        AiTool tool = toolRegistry.get(tc.name());
                        if (tool != null && tool.definition().readOnly()) {
                            // 找到对应的 tool 消息内容（从 DB 读最新）
                            List<AiMessage> recentMsgs = messageMapper.selectByConversationId(conversationId);
                            for (int i = recentMsgs.size() - 1; i >= 0; i--) {
                                AiMessage m = recentMsgs.get(i);
                                if (AiMessage.ROLE_TOOL.equals(m.getRole()) && tc.id().equals(m.getToolCallId())) {
                                    contextMessages.add(ChatMessage.tool(tc.id(), m.getContent()));
                                    break;
                                }
                            }
                        }
                    }
                    loopCount++;
                }
                // 循环上限终止
                String limitMsg = "已达到工具调用上限，请简化请求后重试。";
                UUID msgId = conversationService.appendAssistantMessage(conversationId, limitMsg, null);
                send(emitter, "done", Map.of("messageId", msgId.toString()));
                emitter.complete();
                ping.cancel(false);
            } catch (ServiceException e) {
                log.warn("[AI] 助手对话异常: {}", e.getMessage());
                auditRecorder.record(callContext, AiFunctionType.ASSISTANT_CHAT,
                        resolvedModel.model(), 0, null, null,
                        Constants.AiInvocationStatus.FAILED, "6002");
                sendError(emitter, ErrorCodeConstants.AI_CALL_FAILED.code(),
                        e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.warn("[AI] 助手对话异常: {}", e.getMessage());
                sendError(emitter, ErrorCodeConstants.AI_CALL_FAILED.code(),
                        ErrorCodeConstants.AI_CALL_FAILED.msg());
                emitter.complete();
            } finally {
                ping.cancel(false);
            }
        });
        return emitter;
    }

    @Override
    public SseEmitter approve(UUID userId, UUID workspaceId, String confirmToken) {
        AiConfirmTokenService.ConfirmPayload payload = confirmTokenService.requireValid(
                confirmToken, userId, workspaceId);
        // 执行写工具
        AiToolContext toolCtx = new AiToolContext(userId, workspaceId, null);
        String result = writeToolExecutor.execute(toolCtx, payload.toolName(), payload.arguments());
        // 落 tool 消息
        conversationService.appendToolMessage(payload.conversationId(), payload.toolCallId(), result);

        // 回填 LLM → 流式生成最终答复
        AiConversation conversation = conversationMapper.selectById(payload.conversationId());
        ResolvedChatModel resolvedModel = requireChatModel(userId, workspaceId, null);
        AiCallContext callContext = new AiCallContext(userId, workspaceId, null);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> { cancelled.set(true); emitter.complete(); });

        ScheduledFuture<?> ping = pingScheduler.scheduleAtFixedRate(() -> {
            try { emitter.send(SseEmitter.event().comment("ping")); }
            catch (Exception e) { cancelled.set(true); }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        Thread.startVirtualThread(() -> {
            try {
                List<ChatMessage> contextMessages = buildContextMessagesFromHistory(
                        payload.conversationId(), payload.assistantMessageId(), payload.toolCallId(), result);
                List<ToolDefinition> toolDefs = buildToolDefinitions(userId, workspaceId);
                ChatCallOptions options = toolDefs.isEmpty()
                        ? ChatCallOptions.defaults()
                        : ChatCallOptions.withTools(toolDefs);
                long start = System.currentTimeMillis();
                provider.streamWithTools(resolvedModel, contextMessages, options,
                        new ToolStreamCallbacks() {
                            @Override
                            public void onDelta(String content) {
                                if (!cancelled.get()) {
                                    send(emitter, "delta", Map.of("content", content));
                                }
                            }
                            @Override
                            public void onToolCalls(List<ToolCall> tc) { /* approve 后不再触发写工具 */ }
                            @Override
                            public void onFinish(String fc, Integer pt, Integer ct) {
                                UUID msgId = conversationService.appendAssistantMessage(
                                        payload.conversationId(), fc, null);
                                auditRecorder.record(callContext, AiFunctionType.ASSISTANT_CHAT,
                                        resolvedModel.model(), System.currentTimeMillis() - start,
                                        pt, ct, Constants.AiInvocationStatus.SUCCESS, null);
                                send(emitter, "done", Map.of("messageId", msgId.toString()));
                                emitter.complete();
                            }
                        }, cancelled);
            } catch (Exception e) {
                log.warn("[AI] approve 回填异常: {}", e.getMessage());
                sendError(emitter, ErrorCodeConstants.AI_CALL_FAILED.code(),
                        ErrorCodeConstants.AI_CALL_FAILED.msg());
                emitter.complete();
            } finally {
                ping.cancel(false);
            }
        });
        return emitter;
    }

    @Override
    public void cancel(UUID userId, UUID workspaceId, String confirmToken) {
        AiConfirmTokenService.ConfirmPayload payload = confirmTokenService.requireValid(
                confirmToken, userId, workspaceId);
        // 落一条 tool 消息："用户已取消该操作"（供后续上下文感知，详细设计 3.3.2）
        conversationService.appendToolMessage(payload.conversationId(), payload.toolCallId(),
                "{\"cancelled\":true,\"message\":\"用户已取消该操作\"}");
    }

    // ======================== 内部方法 ========================

    /**
     * 构建上下文消息：system + 最近 10 轮（user+assistant）+ tool 消息 + 悬空补偿
     */
    private List<ChatMessage> buildContextMessages(UUID conversationId, String userContent) {
        String systemPrompt = promptAssembler.loadSystemPrompt(AiFunctionType.ASSISTANT_CHAT);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));

        // 加载历史消息
        List<AiMessage> history = messageMapper.selectByConversationId(conversationId);
        // 悬空补偿：检测带 tool_calls 但缺 tool 消息的 assistant 消息
        List<AiMessage> compensated = compensateDanglingToolCalls(history);
        // 取最近 N 轮（从补偿后的列表尾部截取）
        int startIdx = Math.max(0, compensated.size() - CONTEXT_HISTORY_ROUNDS * 2);
        for (int i = startIdx; i < compensated.size(); i++) {
            AiMessage msg = compensated.get(i);
            switch (msg.getRole()) {
                case AiMessage.ROLE_USER -> messages.add(ChatMessage.user(msg.getContent()));
                case AiMessage.ROLE_ASSISTANT -> {
                    List<ToolCall> toolCalls = deserializeToolCalls(msg.getToolCalls());
                    messages.add(ChatMessage.assistant(msg.getContent(), toolCalls));
                }
                case AiMessage.ROLE_TOOL -> messages.add(ChatMessage.tool(msg.getToolCallId(), msg.getContent()));
            }
        }
        return messages;
    }

    /**
     * approve 后构建上下文：从 DB 读取完整历史（含刚落库的 tool 消息）
     */
    private List<ChatMessage> buildContextMessagesFromHistory(UUID conversationId,
                                                              UUID assistantMessageId,
                                                              String toolCallId,
                                                              String toolResult) {
        return buildContextMessages(conversationId, null);
    }

    /**
     * 悬空补偿：带 tool_calls 但缺少对应 tool 消息的 assistant 消息，自动补齐"操作已超时未执行"
     */
    private List<AiMessage> compensateDanglingToolCalls(List<AiMessage> history) {
        // 收集所有 tool_call_id
        Map<String, Boolean> toolCallIds = new LinkedHashMap<>();
        for (AiMessage msg : history) {
            if (AiMessage.ROLE_TOOL.equals(msg.getRole()) && msg.getToolCallId() != null) {
                toolCallIds.put(msg.getToolCallId(), true);
            }
        }
        // 检测悬空的 assistant 消息
        List<AiMessage> result = new ArrayList<>(history);
        for (int i = 0; i < result.size(); i++) {
            AiMessage msg = result.get(i);
            if (AiMessage.ROLE_ASSISTANT.equals(msg.getRole()) && msg.getToolCalls() != null) {
                for (Map<String, Object> tc : msg.getToolCalls()) {
                    String tcId = (String) tc.get("id");
                    if (tcId != null && !toolCallIds.containsKey(tcId)) {
                        // 补齐 tool 消息
                        AiMessage pendingMsg = new AiMessage();
                        pendingMsg.setConversationId(msg.getConversationId());
                        pendingMsg.setRole(AiMessage.ROLE_TOOL);
                        pendingMsg.setToolCallId(tcId);
                        pendingMsg.setContent("{\"error\":\"操作已超时未执行\"}");
                        messageMapper.insert(pendingMsg);
                        toolCallIds.put(tcId, true);
                        result.add(i + 1, pendingMsg);
                        i++; // 跳过刚插入的消息
                    }
                }
            }
        }
        return result;
    }

    /**
     * 构建工具定义列表：只读工具 + 白名单内的写工具
     */
    private List<ToolDefinition> buildToolDefinitions(UUID userId, UUID workspaceId) {
        List<String> writeWhitelist = aiConfigService.getMergedSettings()
                .getOrDefault("assistantWriteToolWhitelist", List.of()) instanceof List<?> l
                ? l.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of("create_bug", "create_plan_draft");

        List<ToolDefinition> defs = new ArrayList<>();
        for (AiTool tool : toolRegistry.all()) {
            AiToolDefinition def = tool.definition();
            if (!def.readOnly() && !writeWhitelist.contains(def.name())) {
                continue; // 写工具不在白名单 → 跳过
            }
            defs.add(new ToolDefinition(def.name(), def.description(), def.paramsSchema()));
        }
        return defs;
    }

    private ResolvedChatModel requireChatModel(UUID userId, UUID workspaceId, String modelId) {
        if (aiConfigService.getResolvedConfig() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        UUID parsedModelId = null;
        if (modelId != null && !modelId.isBlank()) {
            try {
                parsedModelId = UUID.fromString(modelId);
            } catch (IllegalArgumentException e) {
                // 无效 modelId，走默认
            }
        }
        ResolvedChatModel model = aiChatModelService.resolve(parsedModelId);
        if (model == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_NOT_ENABLED);
        }
        return model;
    }

    private AiConversation requireOwned(UUID userId, UUID workspaceId, UUID conversationId) {
        AiConversation conversation = conversationMapper.selectOwned(userId, workspaceId, conversationId);
        if (conversation == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    private String summarizeToolCall(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "query_bugs" -> "查询缺陷列表";
            case "query_plans" -> "查询计划列表";
            case "query_reviews" -> "查询评审列表";
            case "query_cases" -> "检索测试用例";
            case "get_platform_guide" -> "查询平台使用指引";
            case "create_bug" -> "创建缺陷: " + args.getOrDefault("title", "");
            case "create_plan_draft" -> "创建计划草稿: " + args.getOrDefault("name", "");
            default -> "执行 " + toolName;
        };
    }

    @SuppressWarnings("unchecked")
    private List<ToolCall> deserializeToolCalls(List<Map<String, Object>> toolCallsData) {
        if (toolCallsData == null || toolCallsData.isEmpty()) {
            return List.of();
        }
        return toolCallsData.stream()
                .map(m -> new ToolCall(
                        (String) m.get("id"),
                        (String) m.get("name"),
                        m.get("arguments") instanceof Map ? (Map<String, Object>) m.get("arguments") : Map.of()))
                .toList();
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new OpenAiCompatProvider.StreamCancelledException();
        }
    }

    private void sendError(SseEmitter emitter, Object code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(
                    Map.of("code", code, "message", message), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 客户端已断开，忽略
        }
    }

    @PreDestroy
    public void shutdown() {
        pingScheduler.shutdownNow();
    }
}
