package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectRespDTO {

    private String id;
    private String name;
    private String description;
    private String status;
    private Boolean isDefault;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private CreatorInfo createdBy;
    private LocalDateTime createdAt;

    @Data
    public static class CreatorInfo {
        private String id;
        private String name;
    }
}
