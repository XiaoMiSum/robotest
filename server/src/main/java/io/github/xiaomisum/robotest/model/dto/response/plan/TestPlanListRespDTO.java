package io.github.xiaomisum.robotest.model.dto.response.plan;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestPlanListRespDTO {

    private UUID id;
    private String name;
    private String status;
    private String environment;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ExecutorInfo executor;
    private LocalDateTime createdAt;
    private long totalAssociated;
    private long passed;
    private double progressPercent;
    private double passRate;

    @Data
    public static class ExecutorInfo {
        private UUID id;
        private String name;
    }
}
