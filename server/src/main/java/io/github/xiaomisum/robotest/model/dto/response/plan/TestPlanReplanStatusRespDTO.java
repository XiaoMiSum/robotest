package io.github.xiaomisum.robotest.model.dto.response.plan;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestPlanReplanStatusRespDTO {

    private String status;
    private String message;
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
}
