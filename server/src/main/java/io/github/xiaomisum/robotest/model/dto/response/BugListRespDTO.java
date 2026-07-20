package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BugListRespDTO {

    private String id;
    private String title;
    private String severity;
    private String priority;
    private String status;
    private UserInfo reporter;
    private UserInfo assignee;
    private LocalDateTime createdAt;

    @Data
    public static class UserInfo {
        private String id;
        private String name;
    }
}
