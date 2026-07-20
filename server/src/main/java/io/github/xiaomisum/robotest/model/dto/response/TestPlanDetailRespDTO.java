package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TestPlanDetailRespDTO {

    private String id;
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
        private String id;
        private String name;
    }
}
