package io.github.xiaomisum.robotest.model.dto.response.review;

import lombok.Data;

import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestReviewDetailRespDTO {

    private UUID id;
    private String title;
    private String description;
    private String status;
    private InitiatorInfo initiator;
    private List<UUID> participantIds;
    /** 关联需求（按创建时所选顺序，需求已删除时跳过） */
    private List<RequirementRefRespDTO> requirements;
    /** 关联需求原始 ID（含已删除需求），供编辑表单判定是否发生集合变化 */
    private List<UUID> requirementIds;
    private String replanStatus;
    private String replanMessage;
    private LocalDateTime replanFinishedAt;
    private LocalDateTime createdAt;

    @Data
    public static class InitiatorInfo {
        private UUID id;
        private String name;
    }
}
