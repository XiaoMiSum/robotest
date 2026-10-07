package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantStreamBridge;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptScenes;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import io.github.xiaomisum.robotest.service.domain.requirement.RequirementSuggestionParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 助手解析任务（详设 3.5 流式链路 / 4.1 解析流程）：近 20 条会话历史 → 判别段非流式 JSON
 * 分流 clarify / intent / answer → intent 固化预览、answer 走 RAG 限权检索后流式生成。
 *
 * <p>两段模型调用均不携带 taskId（总册 4.3 助手交互调用 task_id = NULL，prompt_scene 归因
 * assistant_parse / assistant_answer）；任何失败先向通道发 error 并置消息 error 再抛出，
 * 由任务引擎落 failed（1000018117 / 1000018255 按失败环节取值）。</p>
 */
@Component
public class AssistantParseHandler implements TaskHandler {

    private static final String TYPE = "assistant_parse";
    private static final String SCENE_ANSWER = "assistant_answer";

    private static final String KIND_CLARIFY = "clarify";
    private static final String KIND_INTENT = "intent";
    private static final String KIND_ANSWER = "answer";

    private static final String EVENT_DELTA = "delta";
    private static final String EVENT_CLARIFY = "clarify";
    private static final String EVENT_PREVIEW = "preview";
    private static final String EVENT_CITATIONS = "citations";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";

    /** 动作类型（详设 4.3 kind 十种取值）与目标类型的映射由服务端固化，不信赖模型输出 */
    private static final Set<String> INTENT_KINDS = Set.of(
            "create_case", "update_case", "complete_case", "batch_tag",
            "create_review", "adjust_review", "view_review_progress",
            "create_plan", "adjust_plan", "view_plan_progress");
    private static final Map<String, String> TARGET_TYPE_BY_KIND = Map.ofEntries(
            Map.entry("create_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("update_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("complete_case", Constants.TraceNodeType.TEST_CASE),
            Map.entry("batch_tag", Constants.TraceNodeType.TEST_CASE),
            Map.entry("create_review", Constants.TraceNodeType.TEST_REVIEW),
            Map.entry("adjust_review", Constants.TraceNodeType.TEST_REVIEW),
            Map.entry("view_review_progress", Constants.TraceNodeType.TEST_REVIEW),
            Map.entry("create_plan", Constants.TraceNodeType.TEST_PLAN),
            Map.entry("adjust_plan", Constants.TraceNodeType.TEST_PLAN),
            Map.entry("view_plan_progress", Constants.TraceNodeType.TEST_PLAN));

    /** 只读问答检索条数与引语长度（与生成链 RAG_TOP_K / RAG_FRAGMENT_CHARS 同口径） */
    private static final int TOP_K = 8;
    private static final int QUOTE_CHARS = 500;
    private static final int FRAGMENT_CHARS = 800;
    private static final int CASE_IDS_MAX = 200;
    private static final int CHANGES_MAX = 30;
    private static final int TITLE_MAX = 100;
    private static final int SUMMARY_MAX = 500;
    /** 预览时效：生成后 10 分钟过期（详设 4.3 expiresAt） */
    private static final int EXPIRE_MINUTES = 10;

    private static final String RAG_UNAVAILABLE_TEXT =
            "当前向量检索能力未就绪，无法基于数据回答（错误码 1000018258）。";
    private static final String NO_HIT_TEXT = "未检索到相关内容，请尝试补充更具体的业务对象或表述。";
    private static final String DEFAULT_SUMMARY = "已解析为以下变更，确认后执行。";

    private static final String JUDGE_SYSTEM =
            "你是测试平台智能助手的意图解析器，只输出 JSON，不输出解释或代码块标记以外的任何文字。";
    private static final String ANSWER_SYSTEM =
            "你是测试平台智能助手的只读问答员，只依据检索来源回答，不编造数据。";

    /** 判别段内置默认（scene = assistant_parse，自定义行覆盖，详设 3.5） */
    private static final String DEFAULT_PROMPT = """
            将「用户」的最新输入解析为以下三类之一，顶层输出 {"kind": "clarify|intent|answer", ...}：
            1. clarify：指令有多种理解、缺少明确业务对象或约束不足时反问澄清：
               {"kind":"clarify","question":"…","options":["…"]}（options 为 2~5 个快捷选项，无法给出时省略）；
               无明确业务对象的模糊指令不得解析为 intent。
            2. intent：明确的数据变更意图，输出 {"kind":"intent","summary":"一句话解析摘要","intent":{...}}：
               - kind 仅限 create_case / update_case / complete_case / batch_tag / create_review /
                 adjust_review / view_review_progress / create_plan / adjust_plan / view_plan_progress；
               - targetTitle：目标名称（创建类按用户语义拟定，调整与进度类给既有对象名称）；
               - targetId：调整与进度查看类必填，取候选清单中的既有对象 ID；
               - projectId：创建类必填，取上下文项目清单中的项目 ID；
               - params：执行参数，用例引用给 "caseIds":["…"]，其余按动作给对应字段值；
                 create_case 多条时给 "cases":[{"title":"…","priority":"P1","precondition":"…",
                 "steps":["…"],"expected":["…"]}]，每条必带 title、至多 20 条，单条可省略 cases
                 改用 targetTitle 与 changes；
               - changes：展示用字段级变更 [{"field":"…","op":"add|replace","value":…}]（进度查看类空数组）；
               - createCount：将创建的记录数（非创建类给 0）。
               ID 只允许填写候选清单中出现的值（原样照抄，不得虚构）；变更必须基于用户指令，不得臆造。
            3. answer：查询、统计与说明类问题，不变更数据，输出
               {"kind":"answer","query":"用于来源检索的检索文本"}（基于用户原话与上下文对象名称提炼）。

            会话历史（时间正序）：
            {{history}}

            当前活跃工作空间与页面上下文：
            {{context}}

            候选测试用例（用例ID | 标题 | 所属项目）：
            {{caseOptions}}

            候选测试评审（评审ID | 名称 | 所属项目）：
            {{reviewOptions}}

            候选测试计划（计划ID | 名称 | 所属项目）：
            {{planOptions}}
            """;

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private AiAssistantConversationMapper conversationMapper;
    @Resource
    private AssistantRefChecker refChecker;
    @Resource
    private AssistantStreamBridge streamBridge;
    @Resource
    private AssistantPromptSupport promptSupport;
    @Resource
    private VectorSearchService vectorSearchService;

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

        StringBuilder streamed = new StringBuilder();
        List<Map<String, Object>> citations = new ArrayList<>();
        int[] tokens = {0, 0};
        try {
            context.report(10, "读取会话上下文");
            List<AiAssistantMessage> history = promptSupport.history(conversation.getId(), messageId);
            AiAssistantMessage trigger = history.stream()
                    .filter(item -> Constants.AiAssistantMessageRole.USER.equals(item.getRole()))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID));
            String triggerContent = ModelOutputSupport.nvl(trigger.getContent()).trim();
            Map<String, String> judgeVariables = judgeVariables(context, conversation, trigger, history);

            context.report(30, "意图判别");
            // 助手交互调用 task_id = NULL、usage 归因 scene（总册 4.3），故不走 context.chat
            AiChatReply judge = context.getModelClient().chat(new AiChatRequest(null, context.getProjectId(),
                    context.getUserId(), TYPE, context.getModel(), JUDGE_SYSTEM,
                    context.prompt(DEFAULT_PROMPT, judgeVariables)));
            tokens[0] += judge.tokensIn();
            tokens[1] += judge.tokensOut();

            Map<String, Object> parsed = parseJudge(judge);
            String kind = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(parsed.get("kind")));
            if (kind == null) {
                throw parseFailed();
            }
            return switch (kind) {
                case KIND_CLARIFY -> handleClarify(parsed, message, channel, tokens);
                case KIND_INTENT -> handleIntent(parsed, message, channel, context, tokens);
                case KIND_ANSWER -> handleAnswer(parsed, triggerContent,
                        message, channel, context, history, streamed, citations, tokens);
                default -> throw parseFailed();
            };
        } catch (ServiceException e) {
            failMessage(message, streamed.toString(), citations, e.getCode());
            sendError(channel, e.getCode(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            int code = ErrorCodeConstants.AI_MODEL_CALL_FAILED.code();
            failMessage(message, streamed.toString(), citations, code);
            sendError(channel, code, ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg());
            throw e;
        }
    }

    // ---------- 分流 ----------

    private TaskResult handleClarify(Map<String, Object> parsed, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel, int[] tokens) {
        String question = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(parsed.get("question")));
        if (question == null) {
            throw parseFailed();
        }
        List<String> options = new ArrayList<>();
        for (String option : ModelOutputSupport.stringList(parsed.get("options"), 5)) {
            options.add(ModelOutputSupport.abbreviate(option, 200));
        }
        String content = question;
        if (!options.isEmpty()) {
            content += "\n\n" + String.join("\n", options.stream().map(option -> "- " + option).toList());
        }
        // 先落盘再发事件：事件是已提交状态的投影，刷新即可对齐（详设 3.5）
        persistDone(message.getId(), content, null, null);
        if (channel != null) {
            channel.send(EVENT_CLARIFY, Map.of("question", question, "options", options));
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        return new TaskResult(Map.of("kind", KIND_CLARIFY), tokens[0], tokens[1]);
    }

    private TaskResult handleIntent(Map<String, Object> parsed, AiAssistantMessage message,
            AssistantStreamBridge.StreamChannel channel, TaskExecutionContext context, int[] tokens) {
        Map<String, Object> intent = buildIntent(ModelOutputSupport.asMap(parsed.get("intent")),
                context.getWorkspaceId());
        String summary = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(parsed.get("summary")));
        persistDone(message.getId(), ModelOutputSupport.abbreviate(
                        summary == null ? DEFAULT_SUMMARY : summary, SUMMARY_MAX), intent, null);
        if (channel != null) {
            channel.send(EVENT_PREVIEW, intent);
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        Map<String, Object> artifact = Map.of(
                "key", "intent-preview",
                "kind", "intent_preview",
                "title", ModelOutputSupport.nvl((String) intent.get("targetTitle")),
                "content", intent);
        return new TaskResult(Map.of("kind", KIND_INTENT, "artifacts", List.of(artifact)),
                tokens[0], tokens[1]);
    }

    private TaskResult handleAnswer(Map<String, Object> parsed, String triggerContent,
            AiAssistantMessage message, AssistantStreamBridge.StreamChannel channel,
            TaskExecutionContext context, List<AiAssistantMessage> history, StringBuilder streamed,
            List<Map<String, Object>> citations, int[] tokens) {
        String query = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(parsed.get("query")));
        if (query == null || query.isBlank()) {
            query = triggerContent;
        }
        if (query.isBlank()) {
            throw parseFailed();
        }
        String searchQuery = query;
        String historyText = AssistantPromptSupport.historyText(history);
        context.report(50, "检索来源");
        List<AiVectorSearchHitRespDTO> hits;
        try {
            hits = vectorSearchService.search(searchQuery,
                    promptSupport.workspaceProjectIds(context.getWorkspaceId()), null, TOP_K, context.getUserId());
        } catch (ServiceException unavailable) {
            // 检索不可用按总册 4.5 提示 1000018258 降级，不编造数据（详设 3.5）
            return staticAnswer(message, channel, RAG_UNAVAILABLE_TEXT, tokens);
        }
        if (hits.isEmpty()) {
            return staticAnswer(message, channel, NO_HIT_TEXT, tokens);
        }

        Map<String, String> titles = new HashMap<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            titles.computeIfAbsent(citationKey(hit),
                    key -> refChecker.titleOf(hit.getEntityType(), hit.getEntityId()));
        }
        Set<String> seen = new HashSet<>();
        for (AiVectorSearchHitRespDTO hit : hits) {
            if (!seen.add(citationKey(hit))) {
                continue;
            }
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("type", hit.getEntityType());
            citation.put("id", hit.getEntityId());
            String title = titles.get(citationKey(hit));
            citation.put("title", title == null ? String.valueOf(hit.getEntityId()) : title);
            citation.put("quote", ModelOutputSupport.abbreviate(hit.getContent(), QUOTE_CHARS));
            citations.add(citation);
        }
        if (channel != null) {
            channel.send(EVENT_CITATIONS, citations);
        }

        context.report(70, "生成回答");
        Map<String, String> answerVariables = Map.of(
                "question", searchQuery,
                "retrievedContext", retrievedContext(hits, titles),
                "history", historyText);
        AiChatReply answer = context.getModelClient().streamChat(
                new AiChatRequest(null, context.getProjectId(), context.getUserId(), SCENE_ANSWER,
                        context.getModel(), ANSWER_SYSTEM,
                        context.getPromptService().render(SCENE_ANSWER, AiPromptScenes.builtin(SCENE_ANSWER),
                                answerVariables)),
                delta -> {
                    // 流式段边写边发；中途失败时 streamed 已含增量，随 error 一并落盘
                    streamed.append(delta);
                    if (channel != null) {
                        channel.send(EVENT_DELTA, Map.of("text", delta));
                    }
                });
        tokens[0] += answer.tokensIn();
        tokens[1] += answer.tokensOut();

        persistDone(message.getId(), answer.content(), null, citations);
        if (channel != null) {
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        Map<String, Object> artifact = Map.of(
                "key", "answer",
                "kind", "answer",
                "title", ModelOutputSupport.abbreviate(triggerContent, TITLE_MAX),
                "content", ModelOutputSupport.nvl(answer.content()));
        return new TaskResult(Map.of("kind", KIND_ANSWER, "artifacts", List.of(artifact)),
                tokens[0], tokens[1]);
    }

    /** 固定文案回答（零命中 / 检索不可用）：citations 为空数组，正文以 delta 回放后直接完成 */
    private TaskResult staticAnswer(AiAssistantMessage message, AssistantStreamBridge.StreamChannel channel,
            String text, int[] tokens) {
        persistDone(message.getId(), text, null, List.of());
        if (channel != null) {
            channel.send(EVENT_CITATIONS, List.of());
            channel.send(EVENT_DELTA, Map.of("text", text));
            channel.send(EVENT_DONE, Map.of("messageId", message.getId()));
            channel.close();
        }
        return new TaskResult(Map.of("kind", KIND_ANSWER, "retrieved", 0), tokens[0], tokens[1]);
    }

    // ---------- 判别段上下文与输出清洗 ----------

    private Map<String, String> judgeVariables(TaskExecutionContext context, AiAssistantConversation conversation,
            AiAssistantMessage trigger, List<AiAssistantMessage> history) {
        List<Map<String, Object>> attachments = trigger.getAttachments();
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("history", AssistantPromptSupport.historyText(history));
        variables.put("context", promptSupport.contextText(context.getWorkspaceId(), attachments, conversation));
        variables.put("caseOptions", promptSupport.caseOptions(context.getWorkspaceId(), attachments));
        variables.put("reviewOptions", promptSupport.reviewOptions(context.getWorkspaceId()));
        variables.put("planOptions", promptSupport.planOptions(context.getWorkspaceId()));
        return variables;
    }

    private static Map<String, Object> parseJudge(AiChatReply judge) {
        try {
            return RequirementSuggestionParser.parseJsonObject(judge.content());
        } catch (ServiceException e) {
            // 判别段输出不可解析 = 意图解析失败（1000018255），区别于模型通道故障 1000018117
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
        }
    }

    /**
     * 意图清洗与固化（详设 4.2 / 4.3）：动作与目标类型服务端判定，引用经活跃工作空间校验
     * （越权或幻觉按 1000018261 拒绝），scope 与过期时间由服务端补齐后写入 intent。
     */
    private Map<String, Object> buildIntent(Map<String, Object> raw, UUID activeWorkspaceId) {
        String kind = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw.get("kind")));
        if (kind == null || !INTENT_KINDS.contains(kind) || activeWorkspaceId == null) {
            throw parseFailed();
        }
        String targetType = TARGET_TYPE_BY_KIND.get(kind);
        UUID targetId = parseUuid(raw.get("targetId"));
        UUID projectId = parseUuid(raw.get("projectId"));
        Set<UUID> caseIds = parseCaseIds(ModelOutputSupport.asMap(raw.get("params")).get("caseIds"));

        if (targetId != null) {
            refChecker.requireActiveWorkspaceRef(activeWorkspaceId, targetType, targetId);
        }
        Set<UUID> caseProjects = refChecker.requireActiveWorkspaceCases(activeWorkspaceId, caseIds);
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(activeWorkspaceId, projectId);
        }
        return assemble(raw, kind, targetType, targetId, projectId, caseIds, caseProjects, activeWorkspaceId);
    }

    private Map<String, Object> assemble(Map<String, Object> raw, String kind, String targetType, UUID targetId,
            UUID projectId, Set<UUID> caseIds, Set<UUID> caseProjects, UUID activeWorkspaceId) {
        // 作用域项目：用例集（须同项目）> 目标对象所属项目 > 指定项目，皆无则解析失败
        UUID scopeProjectId = null;
        if (!caseProjects.isEmpty()) {
            if (caseProjects.size() != 1) {
                throw parseFailed();
            }
            scopeProjectId = caseProjects.iterator().next();
        }
        if (scopeProjectId == null && targetId != null) {
            scopeProjectId = refChecker.projectIdOf(targetType, targetId);
        }
        if (scopeProjectId == null) {
            scopeProjectId = projectId;
        }
        if (scopeProjectId == null) {
            throw parseFailed();
        }
        // 模型同时给出的目标项目与用例 / 目标对象所属项目冲突：视为解析产物不自洽
        if (projectId != null && !projectId.equals(scopeProjectId)) {
            throw parseFailed();
        }
        String targetTitle = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw.get("targetTitle")));
        if (targetTitle == null && targetId != null) {
            targetTitle = refChecker.titleOf(targetType, targetId);
        }
        if (targetTitle == null) {
            throw parseFailed();
        }

        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("kind", kind);
        intent.put("targetType", targetType);
        intent.put("targetTitle", ModelOutputSupport.abbreviate(targetTitle, TITLE_MAX));
        if (targetId != null) {
            intent.put("targetId", targetId);
        }
        intent.put("projectId", scopeProjectId);
        intent.put("createCount", sanitizedCount(raw.get("createCount")));
        intent.put("changes", sanitizedChanges(raw.get("changes")));
        intent.put("params", sanitizedParams(raw.get("params"), caseIds));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("workspaceId", activeWorkspaceId);
        scope.put("projectId", scopeProjectId);
        scope.put("projectName", promptSupport.projectName(scopeProjectId));
        intent.put("scope", scope);
        // 与 jsonb 内时间同口径：ISO-8601 字符串（详设 4.3 expiresAt 默认 10 分钟）
        intent.put("expiresAt", LocalDateTime.now().plusMinutes(EXPIRE_MINUTES).toString());
        return intent;
    }

    private static List<Map<String, Object>> sanitizedChanges(Object raw) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return changes;
        }
        for (Object element : list) {
            if (changes.size() >= CHANGES_MAX) {
                break;
            }
            Map<String, Object> item = ModelOutputSupport.asMap(element);
            String field = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(item.get("field")));
            if (field == null) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", ModelOutputSupport.abbreviate(field, TITLE_MAX));
            change.put("op", "replace".equals(ModelOutputSupport.asString(item.get("op"))) ? "replace" : "add");
            change.put("value", item.get("value") instanceof String value
                    ? ModelOutputSupport.abbreviate(value, 1000) : item.get("value"));
            changes.add(change);
        }
        return changes;
    }

    private static Map<String, Object> sanitizedParams(Object raw, Set<UUID> caseIds) {
        Map<String, Object> params = ModelOutputSupport.asMap(raw);
        // caseIds 统一以清洗后的 UUID 集回填，字符串引用与幻觉项在此收敛
        params.remove("caseIds");
        params.replaceAll((key, value) -> value instanceof String text
                ? ModelOutputSupport.abbreviate(text, 2000) : value);
        if (!caseIds.isEmpty()) {
            params.put("caseIds", List.copyOf(caseIds));
        }
        return params;
    }

    private static int sanitizedCount(Object raw) {
        if (!(raw instanceof Number number)) {
            return 0;
        }
        return Math.max(0, Math.min(number.intValue(), 10_000));
    }

    private static Set<UUID> parseCaseIds(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<UUID> caseIds = new LinkedHashSet<>();
        for (Object element : list) {
            if (caseIds.size() >= CASE_IDS_MAX) {
                break;
            }
            UUID caseId = parseUuid(element);
            if (caseId != null) {
                caseIds.add(caseId);
            }
        }
        return caseIds;
    }

    private static UUID parseUuid(Object raw) {
        String value = ModelOutputSupport.trimToNull(ModelOutputSupport.asString(raw));
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw parseFailed();
        }
    }

    // ---------- 落盘与事件 ----------

    /** 终态落盘（C11 载体仅带 id 与本次字段）：content / intent / citations 按分流择项写入 */
    private void persistDone(UUID messageId, String content, Map<String, Object> intent,
            List<Map<String, Object>> citations) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(messageId);
        carrier.setContent(content);
        if (intent != null) {
            carrier.setIntent(intent);
        }
        if (citations != null) {
            carrier.setCitations(citations);
        }
        carrier.setStatus(Constants.AiAssistantMessageStatus.DONE);
        messageMapper.updateById(carrier);
    }

    /** 失败落盘：已流出的正文一并保留供重载，状态置 error（无条件覆盖，晚于断连的 interrupted 亦须可见） */
    private void failMessage(AiAssistantMessage message, String streamedText,
            List<Map<String, Object>> citations, int code) {
        AiAssistantMessage carrier = new AiAssistantMessage();
        carrier.setId(message.getId());
        carrier.setStatus(Constants.AiAssistantMessageStatus.ERROR);
        if (streamedText != null && !streamedText.isBlank()) {
            carrier.setContent(streamedText);
        }
        if (!citations.isEmpty()) {
            carrier.setCitations(citations);
        }
        messageMapper.updateById(carrier);
    }

    private static void sendError(AssistantStreamBridge.StreamChannel channel, int code, String message) {
        if (channel != null) {
            channel.send(EVENT_ERROR, Map.of("code", code, "msg", ModelOutputSupport.nvl(message)));
            channel.close();
        }
    }

    private static String retrievedContext(List<AiVectorSearchHitRespDTO> hits, Map<String, String> titles) {
        StringBuilder text = new StringBuilder();
        for (AiVectorSearchHitRespDTO hit : hits) {
            String title = titles.get(citationKey(hit));
            text.append("[").append(title == null ? hit.getEntityId() : title).append("]\n")
                    .append(ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(hit.getContent()),
                            FRAGMENT_CHARS)).append("\n\n");
        }
        return text.toString();
    }

    private static String citationKey(AiVectorSearchHitRespDTO hit) {
        return hit.getEntityType() + "|" + hit.getEntityId();
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

    private static ServiceException parseFailed() {
        return ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED);
    }
}
