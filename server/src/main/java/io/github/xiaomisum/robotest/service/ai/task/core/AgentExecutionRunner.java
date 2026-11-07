package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.UserDetailsBridgeImpl;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.config.ToolDeclaration;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.math.BigDecimal;
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
 * （清单拒绝 → 参数校验 → 权限与作用域鉴权 → 执行 → 结果回喂）、预算闸门与心跳；
 * 终态统一落提案 / 执行 / 任务三处并写轨迹。核内没有任何任务形态分支，
 * 形态差异全部收敛在 {@link TaskTypeAdapter}。
 *
 * <p>成本维度：逐跳按模型单价累计（详设 4.3 公式），随心跳与终态落执行级 cost，
 * 预算 cost 维度据此判定；用量明细的 execution_id 归因由 AiChatRequest 承载。</p>
 */
@Component
public class AgentExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionRunner.class);

    /** 提示词场景 = 任务类型（用量按场景归因，与 ai_prompt_template.scene 同口径） */
    private static final int RESULT_ABBREV = 4000;
    private static final int DEFAULT_MAX_STEPS = 40;

    @Resource
    private TraceRecorder traceRecorder;
    @Resource
    private BudgetGuard budgetGuard;
    @Resource
    private AgentToolRegistry toolRegistry;
    @Resource
    private TaskTypeAdapterRegistry adapterRegistry;
    @Resource
    private AiTaskExecutionMapper executionMapper;
    @Resource
    private ExecutionLifecycle executionLifecycle;
    @Resource
    private AiTaskToolMapper taskToolMapper;
    @Resource
    private AiModelClient modelClient;
    @Resource
    private UserDetailsBridgeImpl userDetailsBridge;

    /**
     * 执行入参：任务与执行行由任务资源服务创建，模型由调用方按能力选型解析后传入；
     * 作用域标识经请求头固化（C4）；feedback 为再次执行的重新生成反馈（可空）。
     */
    public record RunContext(AiTask task, AiTaskExecution execution, AiModelConfig model,
            UUID workspaceId, UUID projectId, String feedback) {
    }

    /**
     * 驱动一次执行至终态（异步；经 Spring 代理调用时异步执行，服务层负责触发时机与等待语义）。
     * 任何异常都不外抛——失败路径统一置执行与任务 aborted 并留轨迹。
     */
    @Async
    public void run(RunContext ctx) {
        AiTask task = ctx.task();
        AiTaskExecution execution = ctx.execution();
        long startMs = System.currentTimeMillis();
        TaskTypeAdapter adapter = adapterRegistry.get(task.getType());
        if (adapter == null) {
            terminate(execution, task, ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(),
                    "不支持的任务类型", 0, 0, 0, BigDecimal.ZERO, startMs);
            return;
        }
        Map<String, Object> budget = execution.getBudget() == null ? Map.of() : execution.getBudget();
        int maxSteps = budget.get("maxSteps") instanceof Number number
                ? number.intValue() : DEFAULT_MAX_STEPS;
        List<String> permissions = loadPermissionCodes(task.getSubmittedBy());
        List<AgentToolDefinition> whitelist = signWhitelist(adapter);
        List<ToolDeclaration> declarations = whitelist.stream()
                .map(d -> new ToolDeclaration(d.code(), d.description(), d.paramsSchema()))
                .toList();
        AgentToolContext toolContext = new AgentToolContext(
                task.getSubmittedBy(), ctx.workspaceId(), ctx.projectId());
        List<Message> turns = new ArrayList<>();
        int steps = 0;
        long tokensIn = 0;
        long tokensOut = 0;
        BigDecimal cost = BigDecimal.ZERO;
        try {
            AiChatRequest request = chatRequest(adapter, task, ctx);
            String finalOutput = null;
            for (int hop = 0; hop < maxSteps; hop++) {
                if (budgetGuard.exceeded(budget,
                        BudgetGuard.Consumed.of(steps, elapsedSeconds(startMs), tokensIn, tokensOut, cost))) {
                    terminate(execution, task, ErrorCodeConstants.AI_EXECUTION_FINISHED.code(),
                            "预算耗尽", steps, tokensIn, tokensOut, cost, startMs);
                    return;
                }
                AiChatReply reply = modelClient.chatWithToolDeclarations(request, declarations, turns);
                steps++;
                tokensIn += reply.tokensIn();
                tokensOut += reply.tokensOut();
                cost = cost.add(AiModelClient.calculateCost(ctx.model(), reply.tokensIn(), reply.tokensOut()));
                if (hop == 0 && !reply.toolCalls().isEmpty()
                        && ModelOutputSupport.trimToNull(reply.content()) != null) {
                    // 计划快照：首跳带工具的正文视为计划（可见思考产物，不作为执行依据）
                    Map<String, Object> plan = parsePlan(reply.content());
                    if (!traceRecorder.recordIfActive(execution.getId(), Constants.AiTraceEventType.PLAN,
                            "执行计划", plan) || !executionMapper.markPlan(execution.getId(), plan)) {
                        return;
                    }
                }
                if (!executionMapper.heartbeat(execution.getId(), steps, clamp(tokensIn), clamp(tokensOut), cost)) {
                    // 执行已被外部终止（用户中止 / 清扫）：停跑，终止路径已留轨迹
                    return;
                }
                if (reply.toolCalls().isEmpty()) {
                    finalOutput = reply.content();
                    break;
                }
                turns.add(assistantTurn(reply));
                for (AiToolCall call : reply.toolCalls()) {
                    steps++;
                    if (!traceRecorder.recordIfActive(execution.getId(), Constants.AiTraceEventType.TOOL_CALL,
                            "调用工具 " + call.name(), callTrail(call))) {
                        return;
                    }
                    String result = dispatch(call, whitelist, toolContext, permissions);
                    if (!traceRecorder.recordIfActive(execution.getId(), Constants.AiTraceEventType.TOOL_RESULT,
                            "工具结果 " + call.name(), Map.of("result",
                                    ModelOutputSupport.abbreviate(ModelOutputSupport.nvl(result), RESULT_ABBREV)))) {
                        return;
                    }
                    turns.add(toolTurn(call, result));
                    if (!executionMapper.heartbeat(execution.getId(), steps, clamp(tokensIn), clamp(tokensOut),
                            cost)) {
                        return;
                    }
                    if (budgetGuard.exceeded(budget,
                            BudgetGuard.Consumed.of(steps, elapsedSeconds(startMs), tokensIn, tokensOut, cost))) {
                        terminate(execution, task, ErrorCodeConstants.AI_EXECUTION_FINISHED.code(),
                                "预算耗尽", steps, tokensIn, tokensOut, cost, startMs);
                        return;
                    }
                }
            }
            if (finalOutput == null) {
                terminate(execution, task, ErrorCodeConstants.AI_EXECUTION_FINISHED.code(),
                        "步数预算耗尽", steps, tokensIn, tokensOut, cost, startMs);
                return;
            }
            finish(adapter, task, execution, finalOutput, steps, tokensIn, tokensOut, cost, startMs);
        } catch (ServiceException e) {
            terminate(execution, task, e.getCode(), e.getMessage(), steps, tokensIn, tokensOut, cost, startMs);
        } catch (DuplicateKeyException e) {
            // 提案键执行内重复：适配器生成缺陷，按幂等冲突终止（总册 2.6.4 uk 兜底）
            terminate(execution, task, ErrorCodeConstants.AI_PROPOSAL_ALREADY_CONFIRMED.code(),
                    "提案键重复", steps, tokensIn, tokensOut, cost, startMs);
        } catch (RuntimeException e) {
            log.warn("[AI] 执行核运行失败 taskId={} type={}", task.getId(), task.getType(), e);
            terminate(execution, task, ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "执行核运行异常: " + e.getMessage(), steps, tokensIn, tokensOut, cost, startMs);
        }
    }

    // ---------- 终态 ----------

    /** 末跳产出交给共享生命周期，以单个短事务提交执行、提案、任务投影与终态轨迹。 */
    private void finish(TaskTypeAdapter adapter, AiTask task, AiTaskExecution execution,
            String finalOutput, int steps, long tokensIn, long tokensOut, BigDecimal cost, long startMs) {
        if (ModelOutputSupport.trimToNull(finalOutput) == null) {
            terminate(execution, task, ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                    "模型无有效输出", steps, tokensIn, tokensOut, cost, startMs);
            return;
        }
        List<ProposalDraft> drafts = adapter.buildProposals(finalOutput, task.getInput());
        Map<String, Object> resultMeta = adapter.buildResultMeta(finalOutput, task.getInput());
        executionLifecycle.succeed(task.getId(), execution.getId(), drafts, resultMeta,
                new ExecutionLifecycle.Usage(steps, clamp(tokensIn), clamp(tokensOut), cost,
                        durationMs(startMs)));
    }

    /** 失败 / 中止路径：执行 aborted（terminate_code）+ 任务 aborted（带终止原因），轨迹保留可查（人工验收 5） */
    private void terminate(AiTaskExecution execution, AiTask task, int code, String message,
            int steps, long tokensIn, long tokensOut, BigDecimal cost, long startMs) {
        executionLifecycle.terminate(task.getId(), execution.getId(), code,
                ModelOutputSupport.nvl(message),
                new ExecutionLifecycle.Usage(steps, clamp(tokensIn), clamp(tokensOut), cost,
                        durationMs(startMs)));
    }

    // ---------- 工具分发（不信赖模型：清单外 / 参数非法 / 权限不足一律回喂，不中断循环） ----------

    private String dispatch(AiToolCall call, List<AgentToolDefinition> whitelist,
            AgentToolContext toolContext, List<String> permissions) {
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
        if (definition.permissionCode() != null && !permissions.contains(definition.permissionCode())) {
            return errorJson("权限不足: " + definition.permissionCode());
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

    /** 执行期权限鉴权（总册 2.6.5 permission_code）：按发起人身份加载权限点，NULL 继承即免检 */
    private List<String> loadPermissionCodes(UUID userId) {
        var details = userDetailsBridge.loadByUserId(userId.toString());
        return details instanceof LoginUser loginUser ? loginUser.getPermissionCodes() : List.of();
    }

    // ---------- 请求与轮次构造 ----------

    private AiChatRequest chatRequest(TaskTypeAdapter adapter, AiTask task, RunContext ctx) {
        String userPrompt = "任务类型: " + task.getType()
                + "\n任务输入:\n" + JsonUtils.toJsonString(task.getInput())
                + (ModelOutputSupport.trimToNull(ctx.feedback()) == null
                        ? "" : "\n用户反馈:\n" + ctx.feedback());
        return new AiChatRequest(task.getId(), ctx.execution().getId(), ctx.projectId(), task.getSubmittedBy(),
                task.getType(), ctx.model(), adapter.systemPrompt(), userPrompt);
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

    private static long elapsedSeconds(long startMs) {
        return (System.currentTimeMillis() - startMs) / 1000;
    }

    private static int durationMs(long startMs) {
        return clampMs(System.currentTimeMillis() - startMs);
    }

    /** int 列兜底：token / 时长累计超上限时截断（明细归因以 ai_usage_log 为准） */
    private static int clamp(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private static int clampMs(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
