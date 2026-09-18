package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewConclusionRespDTO;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;

/**
 * review_conclusion 生成共享指令与输出断言（06 §5.2）：自动（完整链路有界重试）与手动（SSE）两路径复用，
 * 保证 reason/keyFindings 结构约束一致。
 */
final class ReviewConclusionSupport {

    static final String TASK_INSTRUCTION = """
            请基于给定评审统计与不通过用例采样，输出面向测试负责人的评审结论说明。
            reason：一段话说明结论依据与后续建议，不超过 300 字；
            keyFindings：列出关键发现，每条一句话、不超过 80 字，无明显发现时输出空数组。""";

    private ReviewConclusionSupport() {
    }

    /**
     * 输出结构断言：schema 允许 keyFindings 为空数组（无发现），但不得为 null 且条目需非空，与提示词约束一致。
     */
    static void assertOutput(AiReviewConclusionRespDTO out) {
        if (out.getKeyFindings() == null) {
            throw new AiOutputValidator.OutputValidationException("keyFindings 不能为 null，无发现请输出空数组");
        }
        for (String finding : out.getKeyFindings()) {
            if (finding == null || finding.isBlank()) {
                throw new AiOutputValidator.OutputValidationException("keyFindings 条目不允许为空");
            }
        }
    }
}