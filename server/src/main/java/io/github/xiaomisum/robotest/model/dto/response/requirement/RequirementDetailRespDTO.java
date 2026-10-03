package io.github.xiaomisum.robotest.model.dto.response.requirement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 需求详情（详设 3.4–3.7 的统一响应）。
 */
@Data
public class RequirementDetailRespDTO {

    private UUID id;
    private String code;
    private String title;
    private String description;
    private UUID moduleId;
    private String moduleName;
    private String systemVersion;
    private String status;
    private String priority;
    private UUID ownerId;
    private String ownerName;
    private List<String> tags;
    private String source;
    /** 来源附件（source = import 时）；文件管理模块落地前恒为 null */
    private SourceFile sourceFile;
    private LocalDateTime confirmedAt;
    /** 覆盖状态：追溯侧未接入时为 null（前端展示「—」） */
    private String coverageStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class SourceFile {

        private UUID fileId;
        private String name;
    }
}
