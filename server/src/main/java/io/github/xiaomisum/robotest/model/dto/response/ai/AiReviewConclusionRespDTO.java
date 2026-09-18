package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 评审结论（06 §5.2）：verdict 由评审域确定性判定（不依赖 LLM），
 * reason/keyFindings 由 LLM 生成，statistics 为 SQL 精确计算。
 */
@Data
public class AiReviewConclusionRespDTO {

    /** 评审结论判定（PASS/FAIL/INCONCLUSIVE），LLM 不予产出、不参与生成 */
    private String verdict;

    @NotBlank
    @Size(max = 2000)
    private String reason;

    /** 无发现时允许空数组（null 由业务断言拒绝），因此不加 @NotEmpty */
    @Size(max = 30)
    private List<@NotBlank @Size(max = 500) String> keyFindings;

    private AiReviewSummaryRespDTO.Statistics statistics;

    private LocalDateTime generatedAt;
}