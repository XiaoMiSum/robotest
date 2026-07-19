package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectCreateReqDTO {

    @NotBlank(message = "项目名称不能为空")
    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
