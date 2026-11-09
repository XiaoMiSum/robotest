package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionControl;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * assistant_parse 执行策略（分册 04 §4.1）：近 20 条会话历史装配为 agent 提示词，
 * 交既有 {@link AssistantToolLoop} 执行工具调用循环（查询回填、澄清 / 变更终止），
 * 终态按 clarify / intent / answer 三路落盘并发事件——先落盘再发事件（详设 3.5）。
 *
 * <p>共享执行控制按模型跳计步（查询工具不额外计步），5 跳硬上限由循环自身约束、
 * 与预算护栏先到者终止；用户中止 / 预算耗尽 / 终态竞争失败置消息 interrupted
 * （非模型或系统错误），模型或系统错误才置 error；任何失败先落盘再发 error 事件。
 * 模型调用不携带 taskId（总册 4.3，usage 逐跳归因 assistant_parse / assistant_answer）。</p>
 */
@Component
public class AssistantExecutionStrategy implements TaskExecutionStrategy {

    private static final String KIND_CLARIFY = "clarify";
    private static final String KIND_INTENT = "intent";
    private static final String KIND_ANSWER = "answer";

    private static final String EVENT_DELTA = "delta";
    private static final String EVENT_CLARIFY = "clarify";
    private static final String EVENT_PREVIEW = "preview";
    private static final String EVENT_CITATIONS = "citations";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    private static final int TITLE_MAX = 100;
    private static final String DEFAULT_SUMMARY = "已解析为以下变更，确认后执行。";

    /** agent 固定角色行；场景提示词主体可经配置中心自定义覆盖（scene = assistant_parse，详设 3.5） */
    private static final String AGENT_SYSTEM =
            "你是测试平台的智能助手，面向测试设计与管理场景，经由工具完成查询、澄清与变更；行为规则见用户消息中的指令。";

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private AiAssistantConversationMapper conversationMapper;
    @Resource
    private AssistantStreamBridge streamBridge;
    @Resource
    private AssistantPromptSupport promptSupport;
    /** 悬空补偿回填依赖下沉组件而非会话服务，避免经任务注册表回到会话服务的循环依赖 */
    @Resource
    private AssistantToolMessageWriter toolMessageWriter;
    @Resource
    private AssistantToolLoop toolLoop;
    @Resource
    private FileResourceService fileResourceService;
    @Resource
    private AiPromptService promptService;
    @Resource
    private AiModelClient modelClient;

    @Override
    public ExecutionResult execute(ExecutionContext ctx, ExecutionControl control) {
        AiTask task = ctx.task();
        UUID messageId = AssistantParseTypeAdapter.requireMessageId(task.getInput());
        AiAssistantMessage message = messageMapper.selectById(messageId);
        AiAssistantConversation conversation = message == null ? null
                : conversationMapper.selectById(message.getConversationId());
        AssistantStreamBridge.StreamChannel channel = streamBridge.get(messageId);
        if (message == null || conversation == null) {
            // 会话在执行前被删除（详设 3.4）：任务静默收尾，通道经回调回收
            if (channel != null) {
                channel.close();
            }
            return new ExecutionResult(List.of(), Map.of("kind", "missing"));
        }

        try {
            if (ctx.model() == null) {
                // 助手不豁免 chat 模型选型（总册 4.2）：缺模型属配置类系统错误
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED);
            }
            List<AiAssistantMessage> history = promptSupport.history(conversation.getId(), messageId);
            AiAssistantMessage trigger = history.stream()
                    .filter(item -> Constants.AiAssistantMessageRole.USER.equals(item.getRole()))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID));
            String triggerContent = ModelOutputSupport.nvl(trigger.getContent()).trim();
            List<AiChatMedia> medias = triggerMedias(trigger);
            compensateDangling(history);

            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("history", AssistantPromptSupport.historyText(history));
            variables.put("context", promptSupport.contextText(trigger.getAttachments(), conversation));

            // 助手交互调用 task_id = NULL、usage 逐跳归因 scene（总册 4.3），故不走通用 Agent 请求
            AssistantToolLoop.LoopOutcome outcome = toolLoop.run(new AssistantToolLoop.LoopRequest(
                    task.getSubmittedBy(), ctx.workspaceId(), conversation.getId(),
                    AGENT_SYSTEM,
                    promptService.render(task.getType(), ctx.adapter().systemPrompt(), variables),
                    medias, ctx.model(), trigger.getAttachments(), channel),
                    modelClient, hopObserver(ctx, control));
            // 终态竞争最后闸门：中止后迟到的完成不得改写消息终态（总册 4.2）
            control.ensureFinishing();
            return switch (outcome.kind()) {
                case KIND_CLARIFY -> handleClarify(outcome, message, channel);
                case KIND_INTENT -> handleIntent(outcome, conversation, message, channel);
                case KIND_ANSWER -> handleAnswer(outcome, triggerContent, message, channel);
                default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
            };
        } catch (ExecutionControl.InactiveExecutionException | ExecutionControl.BudgetExceededException e) {
            // 用户中止 / 预算耗尽 / 终态竞争失败均非模型或系统错误：消息置 interrupted，任务终态由中止方收尾
            markInterrupted(message);
            throw e;
        } catch (ServiceException e) {
            failMessage(message);
            sendError(channel, e.getCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            int code = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            failMessage(message);
            sendError(channel, code, ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg());
            throw e;
        }
    }

    /** 共享执行控制逐跳介入：调用前闸门，返回后按跳计量（steps 按模型跳计，查询工具不计步） */
    private AssistantToolLoop.HopObserver hopObserver(ExecutionContext ctx, ExecutionControl control) {
        return new AssistantToolLoop.HopObserver() {
            @Override
            public void beforeHop() {
                control.beforeCall();
            }

            @Override
            public void afterHop(int tokensIn, int tokensOut) {
                control.afterModelCall(tokensIn, tokensOut,
                        AiModelClient.calculateCost(ctx.model(), tokensIn, tokensOut));
            }
        };
    }

    // ---------- 终态三路（先落盘再发事件，详设 3.5） ----------

    private ExecutionResult handleClarify(AssistantToolLoop.LoopOutcome outcome, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel) {
        String question = outcome.question();
        List<String> options = outcome.options();
        String content = question;
        if (!options.isEmpty()) {
            content += "\n\n" + String.join("\n", options.stream().map(option -> "- " + option).toList());
        }
        persistDone(message.getId(), content, null, null, outcome.toolCalls());
        if (channel != null) {
            channel.send(EVENT_CLARIFY, Map.of("question", question, "options", options));
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        return new ExecutionResult(List.of(), Map.of("kind", KIND_CLARIFY));
    }

    private ExecutionResult handleIntent(AssistantToolLoop.LoopOutcome outcome,
            AiAssistantConversation conversation, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel) {
        Map<String, Object> intent = outcome.intent();
        persistDone(message.getId(), DEFAULT_SUMMARY, intent, null, outcome.toolCalls());
        rememberProject(conversation, intent);
        if (channel != null) {
            channel.send(EVENT_PREVIEW, intent);
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        Map<String, Object> artifact = Map.of(
                "key", "intent-preview",
                "kind", "intent_preview",
                "title", ModelOutputSupport.abbreviate(
                        ModelOutputSupport.nvl((String) intent.get("targetTitle")), TITLE_MAX),
                "content", intent);
        return new ExecutionResult(List.of(),
                Map.of("kind", KIND_INTENT, "artifacts", List.of(artifact)));
    }

    private ExecutionResult handleAnswer(AssistantToolLoop.LoopOutcome outcome, String triggerContent,
            AiAssistantMessage message, AssistantStreamBridge.StreamChannel channel) {
        List<Map<String, Object>> citations = outcome.citations();
        String answer = ModelOutputSupport.nvl(outcome.answer());
        persistDone(message.getId(), answer, null, citations, outcome.toolCalls());
        if (channel != null) {
            if (!citations.isEmpty()) {
                channel.send(EVENT_CITATIONS, citations);
            }
            // 非流式末跳整段回放（详设 3.5 delta 通道；staticAnswer 同口径）
            channel.send(EVENT_DELTA, Map.of("text", answer));
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        Map<String, Object> artifact = Map.of(
                "key", "answer",
                "kind", "answer",
                "title", ModelOutputSupport.abbreviate(triggerContent, TITLE_MAX),
                "content", answer);
        return new ExecutionResult(List.of(),
                Map.of("kind", KIND_ANSWER, "artifacts", List.of(artifact)));
    }

    // ---------- 上下文维护 ----------

    /**
     * 悬空补偿（详设 4.1）：带 tool_calls 但缺回填且未完成的 assistant 消息补「未执行」tool 消息，
     * 保证下一轮上下文协议配对。正常流程仅 done 终态消息带 tool_calls，本路径为防御性兜底。
     */
    private void compensateDangling(List<AiAssistantMessage> history) {
        Set<String> backfilled = new HashSet<>();
        for (AiAssistantMessage item : history) {
            if (Constants.AiAssistantMessageRole.TOOL.equals(item.getRole()) && item.getToolCallId() != null) {
                backfilled.add(item.getToolCallId());
            }
        }
        for (AiAssistantMessage item : history) {
            if (!Constants.AiAssistantMessageRole.ASSISTANT.equals(item.getRole())
                    || item.getToolCalls() == null || item.getToolCalls().isEmpty()
                    || Constants.AiAssistantMessageStatus.DONE.equals(item.getStatus())) {
                continue;
            }
            for (Map<String, Object> call : item.getToolCalls()) {
                String callId = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(call.get("id")));
                if (callId != null && backfilled.add(callId)) {
                    toolMessageWriter.appendToolMessage(item.getConversationId(), callId,
                            "{\"error\":\"操作已超时未执行\"}");
                }
            }
        }
    }

    /** 会话私有项目状态写入（详设 4.4）：intent 固化即记录到 context_snapshot，后续消息解析优先采用 */
    private void rememberProject(AiAssistantConversation conversation, Map<String, Object> intent) {
        Map<String, Object> scope = ModelOutputSupport.asMap(intent.get("scope"));
        Object projectId = scope.get("projectId");
        if (projectId == null) {
            return;
        }
        Map<String, Object> snapshot = conversation.getContextSnapshot() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(conversation.getContextSnapshot());
        snapshot.put("projectId", String.valueOf(projectId));
        snapshot.put("projectName", ModelOutputSupport.nvl(String.valueOf(scope.get("projectName"))));
        AiAssistantConversation carrier = new AiAssistantConversation();
        carrier.setId(conversation.getId());
        carrier.setContextSnapshot(snapshot);
        conversationMapper.updateById(carrier);
    }

    // ---------- 附图媒体 ----------

    /** 当前消息附图直传媒体（详设 4.1）：读回字节随本轮模型调用携带；历史消息附图不回传 */
    private List<AiChatMedia> triggerMedias(AiAssistantMessage trigger) {
        List<Map<String, Object>> images = trigger.getImages();
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<AiChatMedia> medias = new ArrayList<>();
        for (Map<String, Object> image : images) {
            FileContent content = fileResourceService.readBytes(imageFileId(image.get("fileId")));
            medias.add(new AiChatMedia(imageMimeType(content.fileName(), content.contentType()),
                    content.content()));
        }
        return medias;
    }

    /** 落盘引用中的文件标识（服务端写入，正常不可缺）：损坏数据按附件非法拒绝，不静默丢图 */
    private static UUID imageFileId(Object raw) {
        String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw));
        if (value == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID);
        }
    }

    /** 扩展名（小写）判图像白名单：与正文贴图 / 缺陷附件同口径 */
    private static String extension(String fileName) {
        String ext = fileName == null ? null : StringUtils.getFilenameExtension(fileName);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    /** 直传 MIME：优先存储归一化类型，非 image/* 时按扩展名回落（白名单内必命中） */
    private static String imageMimeType(String fileName, String contentType) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        return switch (extension(fileName)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    // ---------- 落盘与事件 ----------

    /**
     * 终态落盘（C11 载体仅带 id 与本次字段）：content / intent / citations 按分流择项写入；
     * toolCalls 为累计的模型调用载荷（审计轨迹，详设 2 消息表），空则不落。
     */
    private void persistDone(UUID messageId, String content, Map<String, Object> intent,
            List<Map<String, Object>> citations, List<Map<String, Object>> toolCalls) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(messageId);
        carrier.setContent(content);
        if (intent != null) {
            carrier.setIntent(intent);
        }
        if (citations != null) {
            carrier.setCitations(citations);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            carrier.setToolCalls(toolCalls);
        }
        carrier.setStatus(Constants.AiAssistantMessageStatus.DONE);
        messageMapper.updateById(carrier);
    }

    /** 用户中止 / 预算耗尽：仅 streaming 态条件置 interrupted，不覆盖已落终态的消息（C11 部分更新） */
    private void markInterrupted(AiAssistantMessage message) {
        messageMapper.update(null, new LambdaUpdateWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getId, message.getId())
                .eq(AiAssistantMessage::getStatus, Constants.AiAssistantMessageStatus.STREAMING)
                .set(AiAssistantMessage::getStatus, Constants.AiAssistantMessageStatus.INTERRUPTED));
    }

    /** 失败落盘：状态置 error（无条件覆盖，晚于断连的 interrupted 亦须可见） */
    private void failMessage(AiAssistantMessage message) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(message.getId());
        carrier.setStatus(Constants.AiAssistantMessageStatus.ERROR);
        messageMapper.updateById(carrier);
    }

    private static void sendError(AssistantStreamBridge.StreamChannel channel, int code, String message) {
        if (channel != null) {
            channel.send(EVENT_ERROR, Map.of("code", code, "msg", ModelOutputSupport.nvl(message)));
            channel.close();
        }
    }
}
