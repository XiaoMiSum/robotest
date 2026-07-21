package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

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
    private LocalDateTime createdAt;

    @Data
    public static class ExecutorInfo {
        private UUID id;
        private String name;
    }
}
