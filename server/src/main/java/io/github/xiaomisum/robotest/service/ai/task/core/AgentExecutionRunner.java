package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.config.ToolDeclaration;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.AgentExecutionPreparation;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 执行核（总册 4.2）：通用循环驱动——计划快照入轨迹、白名单内工具调用
 * （清单拒绝 → 参数校验 → 作用域鉴权 → 执行 → 结果回喂）、预算闸门与心跳；
 * 终态统一落提案 / 执行 / 任务三处并写轨迹。核内没有任何任务形态分支，
 * 形态差异全部收敛在 {@link TaskTypeAdapter}。
 *
 * <p>成本维度：逐跳按模型单价累计（详设 4.3 公式），随心跳与终态落执行级 cost，
 * 预算 cost 维度据此判定；用量明细的 execution_id 归因由 AiChatRequest 承载。</p>
 */
@Component
public class AgentExecutionRunner implements TaskExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionRunner.class);

    /** 提示词场景 = 任务类型（用量按场景归因，与 ai_prompt_template.scene 同口径） */
    private static final int RESULT_ABBREV = 4000;
    private static final int DEFAULT_MAX_STEPS = 40;

    @Resource
    private AgentToolRegistry toolRegistry;
    @Resource
    private AiTaskToolMapper taskToolMapper;
    @Resource
    private AiModelClient modelClient;
    @Resource
    private AiPromptService promptService;
    @Resource
    private AgentToolArgumentValidator argumentValidator;

    /** 驱动 Agent 循环并返回提案与结果元数据；异步和终态统一由分派器负责。 */
    @Override
    public ExecutionResult execute(ExecutionContext ctx, ExecutionControl control) {
        AiTask task = ctx.task();
        TaskTypeAdapter adapter = ctx.adapter();
        control.beforeCall();
        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                task.getId(), task.getSubmittedBy(), ctx.workspaceId(), ctx.projectId(), task.getInput(),
                ctx.feedback()));
        int maxSteps = control.maxSteps(DEFAULT_MAX_STEPS);
        List<AgentToolDefinition> whitelist = signWhitelist(adapter);
        List<ToolDeclaration> declarations = whitelist.stream()
                .map(d -> new ToolDeclaration(d.code(), d.description(), d.paramsSchema()))
                .toList();
        AgentToolContext toolContext = new AgentToolContext(
                task.getSubmittedBy(), ctx.workspaceId(), ctx.projectId(), task.getId(), task.getType(),
                task.getInput());
        List<Message> turns = new ArrayList<>();
        AiChatRequest request = chatRequest(adapter, preparation, task, ctx, control);
        String finalOutput = null;
        for (int hop = 0; hop < maxSteps; hop++) {
            control.beforeCall();
            AiChatReply reply = modelClient.chatWithToolDeclarations(request, declarations, turns, false);
            control.afterModelCall(reply.tokensIn(), reply.tokensOut(),
                    AiModelClient.calculateCost(ctx.model(), reply.tokensIn(), reply.tokensOut()));
            control.recordTrace(Constants.AiTraceEventType.AI_DECISION,
                    reply.toolCalls().isEmpty() ? "模型完成任务" : "模型选择调用工具",
                    decisionDetail(request, declarations, reply, task, ctx));
            if (hop == 0 && !reply.toolCalls().isEmpty()
                    && ModelOutputSupport.trimToNull(reply.content()) != null) {
                // 计划快照：首跳带工具的正文视为计划（可见思考产物，不作为执行依据）
                Map<String, Object> plan = parsePlan(reply.content());
                control.recordPlan(plan);
            }
            if (reply.toolCalls().isEmpty()) {
                finalOutput = reply.content();
                break;
            }
            turns.add(assistantTurn(reply));
            for (AiToolCall call : reply.toolCalls()) {
                control.beforeToolCall();
                control.recordTrace(Constants.AiTraceEventType.TOOL_CALL,
                        "调用工具 " + call.name(), callTrail(call));
                String result = dispatch(call, whitelist, toolContext);
                control.recordTrace(Constants.AiTraceEventType.TOOL_RESULT,
                        "工具结果 " + call.name(), Map.of("result",
                                ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(result), RESULT_ABBREV)));
                turns.add(toolTurn(call, result));
                control.afterToolCall();
            }
        }
        if (finalOutput == null) {
            throw new ExecutionControl.BudgetExceededException();
        }
        if (ModelOutputSupport.trimToNull(finalOutput) == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), "模型无有效输出");
        }
        String output = finalOutput;
        ServiceException parseError;
        try {
            return buildResult(preparation, output);
        } catch (ServiceException e) {
            parseError = e;
        }
        if (!adapter.supportsOutputRepair()) {
            recordOutputParseFailure(control, parseError, output);
            throw parseError;
        }
        AiChatReply repairReply;
        try {
            repairReply = callForRepair(request, declarations, control, output, parseError, task, ctx);
        } catch (ServiceException repairCallError) {
            // 自愈调用失败保留原始解析错误，避免用网络错误掩盖真正的输出缺陷
            recordOutputParseFailure(control, parseError, output);
            throw parseError;
        }
        String repaired = repairReply.content();
        if (ModelOutputSupport.trimToNull(repaired) != null) {
            try {
                return buildResult(preparation, repaired);
            } catch (ServiceException repairError) {
                recordOutputParseFailure(control, repairError, repaired);
                throw repairError;
            }
        }
        recordOutputParseFailure(control, parseError, output);
        throw parseError;
    }

    private static ExecutionResult buildResult(AgentExecutionPreparation preparation, String output) {
        List<ProposalDraft> drafts = preparation.proposalParser().apply(output);
        Map<String, Object> resultMeta = preparation.resultMetaParser().apply(output);
        return new ExecutionResult(drafts, resultMeta);
    }

    /** AI 决策事件明细：保留工具名摘要并附请求摘要与完整响应（详设 2.6.3 / 4.2） */
    private static Map<String, Object> decisionDetail(AiChatRequest request, List<ToolDeclaration> declarations,
            AiChatReply reply, AiTask task, ExecutionContext ctx) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("toolCalls", reply.toolCalls().stream().map(AiToolCall::name).toList());
        detail.put("rawRequest", rawRequest(request, declarations, task, ctx));
        detail.put("rawResponse", reply.rawResponse());
        return detail;
    }

    /** 请求摘要只含业务上下文与工具名，systemPrompt / options / 密钥不进轨迹（详设 4.2） */
    private static Map<String, Object> rawRequest(AiChatRequest request, List<ToolDeclaration> declarations,
            AiTask task, ExecutionContext ctx) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("taskType", task.getType());
        context.put("input", task.getInput());
        context.put("feedback", ctx.feedback());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("model", request.model() == null ? null : request.model().getModelName());
        raw.put("promptScene", request.promptScene());
        raw.put("context", context);
        raw.put("tools", declarations.stream().map(ToolDeclaration::name).toList());
        return raw;
    }

    /** 全量解析失败后的一次性回喂：复用系统提示词，附加错误上下文要求模型自愈重出 JSON。 */
    private AiChatReply callForRepair(AiChatRequest request, List<ToolDeclaration> declarations,
            ExecutionControl control, String output, ServiceException parseError, AiTask task, ExecutionContext ctx) {
        String instruction = """
                上一次输出未能通过应用层校验：%s
                请严格按系统提示词重新输出完整 JSON（只输出 JSON）：
                - sourceRefs.id 必须逐字复制「来源需求白名单」中的 UUID，禁止填需求编号或标题；
                - sourceRefs 只输出 id 与 quote，type / title / code 由系统补全；
                - 每条关联规划用例必须输出一条建议。
                上一次原始输出：
                %s
                """.formatted(ModelOutputSupport.nvl(parseError.getMessage()),
                ModelOutputSupport.abbreviate(output, RESULT_ABBREV));
        AiChatRequest repairRequest = new AiChatRequest(request.taskId(), request.executionId(),
                request.projectId(), request.userId(), request.promptScene(), request.model(),
                request.systemPrompt(), request.userPrompt() + "\n\n【输出修正要求】\n" + instruction,
                request.media(), request.onTick());
        control.beforeCall();
        AiChatReply reply = modelClient.chatWithToolDeclarations(repairRequest, declarations, List.of(), false);
        control.afterModelCall(reply.tokensIn(), reply.tokensOut(),
                AiModelClient.calculateCost(request.model(), reply.tokensIn(), reply.tokensOut()));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("errorCode", parseError.getCode());
        detail.put("errorMessage", ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(parseError.getMessage()), 500));
        detail.put("rawRequest", rawRequest(repairRequest, declarations, task, ctx));
        detail.put("rawResponse", reply.rawResponse());
        control.recordTrace(Constants.AiTraceEventType.AI_DECISION, "模型输出解析失败后自愈重试", detail);
        return reply;
    }

    private void recordOutputParseFailure(ExecutionControl control, ServiceException e, String output) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("errorCode", e.getCode());
            detail.put("errorMessage", ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(e.getMessage()), 500));
            detail.put("outputChars", output == null ? 0 : output.length());
            detail.put("outputPreview", ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(output), RESULT_ABBREV));
            control.recordTrace(Constants.AiTraceEventType.AI_DECISION, "模型输出解析失败", detail);
        } catch (Exception traceError) {
            log.debug("[AI] 记录输出解析失败轨迹失败", traceError);
        }
    }

    // ---------- 工具分发（不信赖模型：清单外 / 参数非法一律回喂，不中断循环） ----------

    private String dispatch(AiToolCall call, List<AgentToolDefinition> whitelist,
            AgentToolContext toolContext) {
        AgentToolDefinition definition = whitelist.stream()
                .filter(d -> d.code().equals(call.name()))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            return errorJson("工具不在白名单: " + call.name());
        }
        Map<String, Object> args;
        try {
            args = parseArgs(call);
        } catch (ServiceException e) {
            return errorJson(e.getCode() + ": 工具参数 JSON 非法");
        }
        String invalid = argumentValidator.validate(definition.paramsSchema(), args);
        if (invalid != null) {
            return errorJson("工具参数非法: " + invalid);
        }
        AgentTool tool = toolRegistry.get(definition.code());
        if (tool == null) {
            return errorJson("工具不可执行: " + definition.code());
        }
        try {
            return tool.execute(toolContext, args);
        } catch (ServiceException e) {
            return errorJson(e.getCode() + ": " + ModelOutputSupport.nvl(e.getMessage()));
        } catch (Exception e) {
            log.warn("[AI] 任务工具执行失败 tool={}", definition.code(), e);
            return errorJson("工具执行异常: " + ModelOutputSupport.nvl(e.getMessage()));
        }
    }

    /**
     * 白名单签发：类型适配器声明 ∩ ai_task_tool 启用行 ∩ 代码注册表
     * （总册 3.6.1——表未种子时无工具，循环退化为纯决策）。
     */
    private List<AgentToolDefinition> signWhitelist(TaskTypeAdapter adapter) {
        List<String> declared = adapter.toolCodes();
        if (declared == null || declared.isEmpty()) {
            return List.of();
        }
        Set<String> enabled = taskToolMapper.selectList(new LambdaQueryWrapperX<AiTaskTool>()
                        .eq(AiTaskTool::getEnabled, true)).stream()
                .filter(row -> row.getTaskTypes() != null
                        && (row.getTaskTypes().contains(adapter.type())
                                || row.getTaskTypes().contains("*")))
                .map(AiTaskTool::getToolCode)
                .collect(Collectors.toSet());
        return declared.stream()
                .filter(enabled::contains)
                .map(toolRegistry::definition)
                .filter(Objects::nonNull)
                .toList();
    }

    // ---------- 请求与轮次构造 ----------

    private AiChatRequest chatRequest(TaskTypeAdapter adapter, AgentExecutionPreparation preparation,
            AiTask task, ExecutionContext ctx, ExecutionControl control) {
        String resolvedPrompt = promptService.render(adapter.type(), adapter.systemPrompt(),
                preparation.promptVariables());
        String userPrompt = "任务类型: " + task.getType()
                + "\n可信输入快照:\n" + preparation.inputSnapshot()
                + (ModelOutputSupport.trimToNull(ctx.feedback()) == null
                        ? "" : "\n用户反馈:\n" + ctx.feedback());
        return new AiChatRequest(task.getId(), ctx.execution().getId(), ctx.projectId(), task.getSubmittedBy(),
                task.getType(), ctx.model(), resolvedPrompt, userPrompt, preparation.media(),
                control::heartbeatDuringStream);
    }

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
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 计划快照解析：JSON 对象原样入轨迹，非对象（自由文本）包为摘要 */
    private static Map<String, Object> parsePlan(String content) {
        try {
            Object parsed = JsonUtils.parseObject(content, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
        } catch (Exception ignored) {
            // 非 JSON 正文按摘要落轨迹
        }
        return Map.of("summary", ModelOutputSupport.abbreviate(content, RESULT_ABBREV));
    }

    private static Map<String, Object> callTrail(AiToolCall call) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", call.id());
        entry.put("name", call.name());
        entry.put("arguments", ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(call.arguments()),
                RESULT_ABBREV));
        return entry;
    }

    private static String errorJson(String message) {
        return JsonUtils.toJsonString(Map.of("error", ModelOutputSupport.nvl(message)));
    }

}
