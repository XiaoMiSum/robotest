package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BugListRespDTO {

    private UUID id;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private UserInfo reporter;
    private UserInfo assignee;
    private LocalDateTime createdAt;

    @Data
    public static class UserInfo {
        private UUID id;
        private String name;
    }
}
