package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPriorityRecommendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPriorityRecommendRespDTO;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * AI 优先级推荐实现（详细设计 4.3）：
 * 规则命中直接返回（不经网关、不计限流）；未命中发起 LLM 同步调用（超时 5s，
 * 功能级覆盖网关同步默认读超时 15s），失败返回 priority=null——前端静默忽略。
 */
@Service
public class AiPriorityRecommendServiceImpl implements AiPriorityRecommendService {

    /** 推荐时效价值高于成功率，5s 后即放弃（详细设计 3.3.1） */
    private static final int LLM_TIMEOUT_MILLIS = 5000;

    private static final String TASK_INSTRUCTION = "请根据用例标题与所属模块路径，推荐测试用例优先级（P0-P3）。";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private PriorityRuleEngine priorityRuleEngine;

    @Override
    public AiPriorityRecommendRespDTO recommend(UUID userId, UUID workspaceId, UUID projectId,
            AiPriorityRecommendReqDTO reqDTO) {
        // 规则命中不经 LLM、不计限流；P2 只来自 LLM（规则表不产出，4.3）
        String rulePriority = priorityRuleEngine.match(reqDTO.getTitle());
        if (rulePriority != null) {
            return response(rulePriority, "rule");
        }
        // 未命中 → LLM 兜底；任何失败（上游错误/超时/校验失败）静默返回空推荐，不打扰用户
        try {
            PriorityOut out = aiGatewayService.completeStructured(
                    new AiCallContext(userId, workspaceId, projectId),
                    AiFunctionType.PRIORITY_RECOMMENDATION,
                    TASK_INSTRUCTION,
                    buildBusinessData(reqDTO),
                    new ChatCallOptions(null, null, true, LLM_TIMEOUT_MILLIS),
                    PriorityOut.class,
                    null);
            return response(out.getPriority(), "llm");
        } catch (Exception e) {
            return response(null, "llm");
        }
    }

    /** LLM 输入：用例标题 + 祖先模块路径（无路径则省略，避免空节干扰判定） */
    private String buildBusinessData(AiPriorityRecommendReqDTO reqDTO) {
        StringBuilder data = new StringBuilder();
        data.append("【用例标题】").append(reqDTO.getTitle()).append('\n');
        List<String> ancestors = reqDTO.getAncestorTitles();
        if (ancestors != null && !ancestors.isEmpty()) {
            data.append("【所属模块路径】").append(String.join(" > ", ancestors)).append('\n');
        }
        return data.toString();
    }

    private AiPriorityRecommendRespDTO response(String priority, String source) {
        AiPriorityRecommendRespDTO dto = new AiPriorityRecommendRespDTO();
        dto.setPriority(priority);
        dto.setSource(source);
        return dto;
    }

    /** LLM 结构化输出：仅 priority 字段（source 由后端补充），取值受限 P0-P3 */
    @Data
    public static class PriorityOut {

        @NotBlank(message = "priority 不能为空")
        @Pattern(regexp = "P[0-3]", message = "priority 取值仅允许 P0/P1/P2/P3")
        private String priority;
    }
}
