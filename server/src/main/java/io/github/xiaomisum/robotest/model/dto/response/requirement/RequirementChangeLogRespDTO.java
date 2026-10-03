package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 需求变更记录列表项（详设 3.10）。
 */
@Data
public class RequirementChangeLogRespDTO {

    private UUID id;
    /** title / description / module / status / attribute */
    private String changeType;
    private UUID operatorId;
    private String operatorName;
    private Map<String, Object> beforeSummary;
    private Map<String, Object> afterSummary;
    private LocalDateTime createdAt;
}
