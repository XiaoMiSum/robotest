package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

@Data
public class BugUpdateReqDTO {

    private String title;

    private String severity;

    private String priority;

    private String description;

    private String assigneeId;

    private String status;
}
