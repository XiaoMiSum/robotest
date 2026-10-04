package io.github.xiaomisum.robotest.model.dto.response.trace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 覆盖结论（详设 3.7 查询与 3.8 修正响应同构）。
 */
@Data
public class TraceCoverageRespDTO {

    private UUID requirementId;

    /** covered / partial / uncovered */
    private String coverageStatus;

    /** 判定依据：命中的用例集合、缺口说明、AI 理由摘要 */
    private Map<String, Object> evidence;

    private LocalDateTime aiAnalyzedAt;

    /** 人工复核修正人；非空即人工判定优先 */
    private UUID reviewedBy;

    private String reviewedNote;

    private LocalDateTime reviewedAt;

    private UUID analyzedTaskId;
}
