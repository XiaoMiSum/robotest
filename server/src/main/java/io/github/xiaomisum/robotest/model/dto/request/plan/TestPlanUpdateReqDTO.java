package io.github.xiaomisum.robotest.model.dto.request.plan;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestPlanUpdateReqDTO {

    @Size(min = 1, max = 100, message = "计划名称长度须在 1-100 字之间")
    private String name;

    private String description;

    private UUID executorId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String environment;

    private List<UUID> requirementIds;

    /** 仅关联需求集合发生变化时生效 */
    private Boolean replan;
}
