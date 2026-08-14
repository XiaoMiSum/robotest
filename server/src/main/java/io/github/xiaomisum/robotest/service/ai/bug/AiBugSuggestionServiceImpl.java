package io.github.xiaomisum.robotest.service.ai.bug;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiBugSuggestionReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiBugSuggestionRespDTO;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import io.github.xiaomisum.robotest.service.ai.support.AiTextUtils;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * 缺陷表单智能建议实现（4.5）：上下文仅取表单标题 + 重现步骤（截断 4000 token），
 * 不送入项目其他缺陷数据（数据出域最小化）。
 */
@Service
public class AiBugSuggestionServiceImpl implements AiBugSuggestionService {

    /** 表单建议输入预算（token，4.5：重现步骤超长截断不报错） */
    static final int INPUT_TOKEN_BUDGET = 4000;

    private static final Set<String> SEVERITIES = Set.of(Constants.BugSeverity.FATAL, Constants.BugSeverity.SERIOUS,
            Constants.BugSeverity.GENERAL, Constants.BugSeverity.MINOR);
    private static final Set<String> PRIORITIES = Set.of(Constants.BugPriority.HIGH, Constants.BugPriority.MEDIUM,
            Constants.BugPriority.LOW);

    /** 模板 formatConstraint 仅覆盖标题长度与 severity 枚举，priority/reason 由本指令 + 输出校验补齐（3.1） */
    private static final String TASK_INSTRUCTION = """
            请基于缺陷描述优化标题并建议严重等级与优先级。输出单个 JSON 对象，仅含四个字段：\
            optimizedTitle（≤100 字符）、severity（仅 fatal/serious/general/minor）、\
            priority（仅 high/medium/low）、reason（一句话说明建议依据）。\
            输出结构必须严格遵循如下示例（字段名、类型、层级完全一致）：\
            {"optimizedTitle": "优化后的缺陷标题", "severity": "serious", "priority": "high", "reason": "建议依据的一句话说明"}。""";

    @Resource
    private AiGatewayService aiGatewayService;

    @Override
    public AiBugSuggestionRespDTO suggest(UUID userId, UUID workspaceId, UUID projectId,
            AiBugSuggestionReqDTO reqDTO) {
        SuggestionOut out = aiGatewayService.completeStructured(
                new AiCallContext(userId, workspaceId, projectId),
                AiFunctionType.BUG_FORM_SUGGESTION,
                TASK_INSTRUCTION,
                buildBusinessData(reqDTO),
                ChatCallOptions.json(),
                SuggestionOut.class,
                this::assertOut);
        AiBugSuggestionRespDTO resp = new AiBugSuggestionRespDTO();
        resp.setOptimizedTitle(out.getOptimizedTitle());
        resp.setSeverity(out.getSeverity());
        resp.setPriority(out.getPriority());
        resp.setReason(out.getReason());
        return resp;
    }

    /** LLM 输入：表单标题 + 重现步骤（超长按 token 预算截断，4.5） */
    private String buildBusinessData(AiBugSuggestionReqDTO reqDTO) {
        StringBuilder data = new StringBuilder();
        data.append("【缺陷标题】").append(reqDTO.getTitle()).append('\n');
        String repro = reqDTO.getReproSteps();
        if (repro != null && !repro.isBlank()) {
            data.append("【重现步骤】\n")
                    .append(AiTextUtils.truncateToTokenBudget(repro, INPUT_TOKEN_BUDGET))
                    .append('\n');
        }
        return data.toString();
    }

    /** 结构断言：枚举合法性 + 标题长度（3.1 响应校验口径，与 @Size 注解互为兜底） */
    private void assertOut(SuggestionOut out) {
        if (!SEVERITIES.contains(out.getSeverity())) {
            throw new AiOutputValidator.OutputValidationException("severity 取值非法：" + out.getSeverity());
        }
        if (!PRIORITIES.contains(out.getPriority())) {
            throw new AiOutputValidator.OutputValidationException("priority 取值非法：" + out.getPriority());
        }
    }

    /** LLM 结构化输出：与响应 DTO 同构，等级枚举由额外断言二次校验 */
    @Data
    public static class SuggestionOut {

        @NotBlank(message = "optimizedTitle 不能为空")
        @Size(max = 300, message = "optimizedTitle 不能超过 300 字符")
        private String optimizedTitle;

        @NotBlank(message = "severity 不能为空")
        private String severity;

        @NotBlank(message = "priority 不能为空")
        private String priority;

        private String reason;
    }
}
