package io.github.xiaomisum.robotest.model.dto.response.plan;

import lombok.Data;

import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestPlanDetailRespDTO {

    private UUID id;
    private String name;
    private String description;
    private String status;
    private String environment;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ExecutorInfo executor;
    /** 关联需求（按创建时所选顺序，需求已删除时跳过） */
    private List<RequirementRefRespDTO> requirements;
    /** 关联需求原始 ID（含已删除需求），供编辑表单判定是否发生集合变化 */
    private List<UUID> requirementIds;
    private String replanStatus;
    private String replanMessage;
    private LocalDateTime replanFinishedAt;
    private LocalDateTime createdAt;

    @Data
    public static class ExecutorInfo {
        private UUID id;
        private String name;
    }
}
