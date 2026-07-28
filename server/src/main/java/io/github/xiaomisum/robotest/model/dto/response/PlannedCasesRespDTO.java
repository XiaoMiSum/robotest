package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 评审/计划当前规划的用例（原始 documentId/caseId 维度，供调整弹窗回显预选）
 */
@Data
public class PlannedCasesRespDTO {

    private UUID documentId;
    private List<UUID> caseIds;
}
