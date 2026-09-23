package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class WorkspaceRespDTO {

    private UUID id;
    private String name;
    private String description;
    private String status;
    private Long memberCount;
    private Long projectCount;
    /** 创建人用户名，由 created_by 批量回查；历史数据无创建人时为 null（前端展示 —） */
    private String createdByName;
    private LocalDateTime createdAt;
}
