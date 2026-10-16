package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 拆解记录列表项（详设 3.11）。
 */
@Data
public class RequirementSplitRecordRespDTO {

    private UUID id;
    /** document（导入文档） */
    private String sourceType;
    private UUID aiTaskId;
    /** pending / adopted / rejected */
    private String status;
    private Map<String, Object> adoptResult;
    private LocalDateTime createdAt;
}
