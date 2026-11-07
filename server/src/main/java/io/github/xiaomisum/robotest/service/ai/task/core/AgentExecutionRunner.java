package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.UserDetailsBridgeImpl;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.config.ToolDeclaration;
import io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
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
 * （清单拒绝 → 参数校验 → 权限与作用域鉴权 → 执行 → 结果回喂）、预算闸门与心跳；
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
    private UserDetailsBridgeImpl userDetailsBridge;

    /** 驱动 Agent 循环并返回提案与结果元数据；异步和终态统一由分派器负责。 */
    @Override
    public ExecutionResult execute(ExecutionContext ctx, ExecutionControl control) {
        AiTask task = ctx.task();
        TaskTypeAdapter adapter = ctx.adapter();
        int maxSteps = control.maxSteps(DEFAULT_MAX_STEPS);
        List<String> permissions = loadPermissionCodes(task.getSubmittedBy());
        List<AgentToolDefinition> whitelist = signWhitelist(adapter);
        List<ToolDeclaration> declarations = whitelist.stream()
                .map(d -> new ToolDeclaration(d.code(), d.description(), d.paramsSchema()))
                .toList();
        AgentToolContext toolContext = new AgentToolContext(
                task.getSubmittedBy(), ctx.workspaceId(), ctx.projectId());
        List<Message> turns = new ArrayList<>();
        AiChatRequest request = chatRequest(adapter, task, ctx);
        String finalOutput = null;
        for (int hop = 0; hop < maxSteps; hop++) {
            control.beforeCall();
            AiChatReply reply = modelClient.chatWithToolDeclarations(request, declarations, turns);
            control.afterModelCall(reply.tokensIn(), reply.tokensOut(),
                    AiModelClient.calculateCost(ctx.model(), reply.tokensIn(), reply.tokensOut()));
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
                String result = dispatch(call, whitelist, toolContext, permissions);
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
        List<ProposalDraft> drafts = adapter.buildProposals(finalOutput, task.getInput());
        Map<String, Object> resultMeta = adapter.buildResultMeta(finalOutput, task.getInput());
        return new ExecutionResult(drafts, resultMeta);
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

    private AiChatRequest chatRequest(TaskTypeAdapter adapter, AiTask task, ExecutionContext ctx) {
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

}
