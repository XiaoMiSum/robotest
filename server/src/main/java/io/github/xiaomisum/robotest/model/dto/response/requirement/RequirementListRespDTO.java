package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 需求列表项（详设 3.2，不含 description 全文）。
 */
@Data
public class RequirementListRespDTO {

    private UUID id;
    private String code;
    private String title;
    private UUID moduleId;
    private String moduleName;
    private String systemVersion;
    private String status;
    private String priority;
    private UUID ownerId;
    private String ownerName;
    private String source;
    private LocalDateTime updatedAt;
}
