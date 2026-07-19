package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectUpdateReqDTO {

    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
