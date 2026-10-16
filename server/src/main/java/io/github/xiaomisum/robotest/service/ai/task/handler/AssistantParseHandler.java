package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantService;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantStreamBridge;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantToolLoop;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 助手解析任务（详设 3.5 流式链路 / 4.1 对话式任务执行）：近 20 条会话历史装配为 agent 提示词，
 * 交 {@link AssistantToolLoop} 执行工具调用循环（查询回填、澄清 / 变更终止），终态按 clarify /
 * intent / answer 三路落盘并发事件——先落盘再发事件，事件是已提交状态的投影（详设 3.5）。
 *
 * <p>模型调用不携带 taskId（总册 4.3 助手交互调用 task_id = NULL，usage 逐跳归因
 * assistant_parse / assistant_answer）；任何失败先向通道发 error 并置消息 error 再抛出，
 * 由任务引擎落 failed（1000018255 / 1000018117 按失败环节取值）。</p>
 */
@Component
public class AssistantParseHandler implements TaskHandler {

    private static final String TYPE = "assistant_parse";

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
    private static final int SUMMARY_MAX = 500;
    private static final String DEFAULT_SUMMARY = "已解析为以下变更，确认后执行。";

    /** agent 固定角色行；场景提示词主体可经配置中心自定义覆盖（scene = assistant_parse，详设 3.5） */
    private static final String AGENT_SYSTEM =
            "你是测试平台的智能助手，面向测试设计与管理场景，经由工具完成查询、澄清与变更；行为规则见用户消息中的指令。";

    /** agent 版内置默认（详设 4.1：工具使用规则 + 多轮上下文，候选清单改由工具按需查询） */
    private static final String DEFAULT_PROMPT = """
            你是测试平台的智能助手，通过调用工具完成查询、澄清与数据变更任务：
            1. 需要项目、文档树、用例、评审、计划、进度或知识信息时先调用对应查询工具，
               一切 ID 只能取工具返回值，不得虚构；
            2. 信息不足、存在歧义或多个候选需要用户抉择时调用 ask_user 澄清（选项来自查询结果
               或上下文，2~5 个）；无据可依时明确说明，不得编造；
            3. 数据变更一律通过变更类工具发起：调用只生成变更预览，落库由用户确认后执行；
               变更参数须满足工具描述中的必填项，字段变更必须基于用户指令，不得臆造；
            4. 最终答复直接输出正文（Markdown，简洁，给出具体对象名称与数值），不复述工具调用过程。

            会话历史（时间正序，最后一条为用户最新输入）：
            {{history}}

            当前上下文（页面引用与会话项目状态）：
            {{context}}
            """;

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private AiAssistantConversationMapper conversationMapper;
    @Resource
    private AssistantStreamBridge streamBridge;
    @Resource
    private AssistantPromptSupport promptSupport;
    @Resource
    private AssistantService assistantService;
    @Resource
    private AssistantToolLoop toolLoop;
    @Resource
    private FileResourceService fileResourceService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return DEFAULT_PROMPT;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        requireMessageId(input);
    }

    @Override
    public void validateInput(Map<String, Object> input, TaskSubmitContext context) {
        UUID messageId = requireMessageId(input);
        // 桥接键只允许本人会话内的 assistant 消息（任务中心直提同码拒绝，防越权解析他人消息）
        if (context == null || context.userId() == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        AiAssistantMessage message = messageMapper.selectById(messageId);
        AiAssistantConversation conversation = message == null ? null
                : conversationMapper.selectById(message.getConversationId());
        if (message == null || !Constants.AiAssistantMessageRole.ASSISTANT.equals(message.getRole())
                || conversation == null || !context.userId().equals(conversation.getUserId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 提交与执行两条路径的能力派生（详设 4.1）：当前消息附图非空 ⇒ `vision`；派生过程不抛错中断任务 */
    @Override
    public String requiredCapability(Map<String, Object> input) {
        AiAssistantMessage message = bridgeMessage(input);
        if (message == null) {
            return null;
        }
        return promptSupport.history(message.getConversationId(), message.getId()).stream()
                .filter(item -> Constants.AiAssistantMessageRole.USER.equals(item.getRole()))
                .reduce((first, second) -> second)
                .filter(item -> item.getImages() != null && !item.getImages().isEmpty())
                .map(item -> AiSettingsReader.CAPABILITY_VISION)
                .orElse(null);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        UUID messageId = requireMessageId(context.getInput());
        AiAssistantMessage message = messageMapper.selectById(messageId);
        AiAssistantConversation conversation = message == null ? null
                : conversationMapper.selectById(message.getConversationId());
        AssistantStreamBridge.StreamChannel channel = streamBridge.get(messageId);
        if (message == null || conversation == null) {
            // 会话在执行前被删除（详设 3.4）：任务静默收尾，通道经回调回收
            if (channel != null) {
                channel.close();
            }
            return new TaskResult(Map.of("kind", "missing"), 0, 0);
        }

        try {
            context.report(10, "读取会话上下文");
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
            variables.put("context", promptSupport.contextText(
                    trigger.getAttachments(), conversation));

            context.report(30, "任务执行");
            // 助手交互调用 task_id = NULL、usage 逐跳归因 scene（总册 4.3），故不走 context.chat
            AssistantToolLoop.LoopOutcome outcome = toolLoop.run(new AssistantToolLoop.LoopRequest(
                    context.getUserId(), context.getWorkspaceId(), conversation.getId(),
                    AGENT_SYSTEM, context.prompt(DEFAULT_PROMPT, variables),
                    medias, context.getModel(), trigger.getAttachments(), channel),
                    context.getModelClient());
            int[] tokens = {outcome.tokensIn(), outcome.tokensOut()};
            return switch (outcome.kind()) {
                case KIND_CLARIFY -> handleClarify(outcome, message, channel, tokens);
                case KIND_INTENT -> handleIntent(outcome, conversation, message, channel, tokens);
                case KIND_ANSWER -> handleAnswer(outcome, triggerContent, message, channel, tokens);
                default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
            };
        } catch (ServiceException e) {
            failMessage(message, e.getCode());
            sendError(channel, e.getCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            int code = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            failMessage(message, code);
            sendError(channel, code, ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg());
            throw e;
        }
    }

    // ---------- 终态三路（先落盘再发事件，详设 3.5） ----------

    private TaskResult handleClarify(AssistantToolLoop.LoopOutcome outcome, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel, int[] tokens) {
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
        return new TaskResult(Map.of("kind", KIND_CLARIFY), tokens[0], tokens[1]);
    }

    private TaskResult handleIntent(AssistantToolLoop.LoopOutcome outcome,
            AiAssistantConversation conversation, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel, int[] tokens) {
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
        return new TaskResult(Map.of("kind", KIND_INTENT, "artifacts", List.of(artifact)),
                tokens[0], tokens[1]);
    }

    private TaskResult handleAnswer(AssistantToolLoop.LoopOutcome outcome, String triggerContent,
            AiAssistantMessage message, AssistantStreamBridge.StreamChannel channel, int[] tokens) {
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
        return new TaskResult(Map.of("kind", KIND_ANSWER, "artifacts", List.of(artifact)),
                tokens[0], tokens[1]);
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
                    assistantService.appendToolMessage(item.getConversationId(), callId,
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

    // ---------- 附图媒体与能力派生 ----------

    /** 能力派生桥接（详设 4.1）：非法输入静默返回 null，交由提交与执行期校验兜底（派生不抛错中断任务） */
    private AiAssistantMessage bridgeMessage(Map<String, Object> input) {
        String value = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(input == null ? null : input.get("messageId")));
        if (value == null) {
            return null;
        }
        try {
            return messageMapper.selectById(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

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

    /** 失败落盘：状态置 error（无条件覆盖，晚于断连的 interrupted 亦须可见） */
    private void failMessage(AiAssistantMessage message, int code) {
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

    private static UUID requireMessageId(Map<String, Object> input) {
        String value = ModelOutputSupport.trimToNull(
                ModelOutputSupport.asString(input == null ? null : input.get("messageId")));
        if (value == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }
}
