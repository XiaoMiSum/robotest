package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AI 异步任务状态（result 常规仅 success 非空；review_check 分批累计写入，
 * running/cancelled 亦可含已产出部分结果）
 */
@Data
public class AiTaskRespDTO {

    private UUID id;
    private String type;
    private UUID targetId;
    private String status;
    private Integer progress;
    private Map<String, Object> result;
    private String errorMessage;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
