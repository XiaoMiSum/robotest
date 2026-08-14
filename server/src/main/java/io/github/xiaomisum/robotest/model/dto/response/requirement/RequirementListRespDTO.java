package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 需求池条目列表项（不含 content 全文）。
 */
@Data
public class RequirementListRespDTO {

    private UUID id;
    private String title;
    private String sourceUrl;
    private String status;
    /** AI 拆分入库标识（US-AI-019），列表展示 AI 徽标 */
    private Boolean aiGenerated;
    private UUID createdBy;
    private String creatorName;
    private LocalDateTime updatedAt;
}
