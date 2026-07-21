package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class BugUpdateReqDTO {

    private String title;

    private String severity;

    private String priority;

    private String description;

    private UUID assigneeId;

    private String status;
}
