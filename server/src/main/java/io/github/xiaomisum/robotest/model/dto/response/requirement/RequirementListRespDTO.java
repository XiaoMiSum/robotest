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
    /** 覆盖状态：AI 总开关关闭或追溯侧未接入时为 null（前端展示「—」） */
    private String coverageStatus;
    private String priority;
    private UUID ownerId;
    private String ownerName;
    private String source;
    private LocalDateTime updatedAt;
}
