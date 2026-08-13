package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 需求池条目详情（含 content 全文）。
 */
@Data
public class RequirementDetailRespDTO {

    private UUID id;
    private String title;
    private String content;
    private String sourceUrl;
    private String status;
    private UUID createdBy;
    private String creatorName;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
