package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestReviewListRespDTO {

    private String id;
    private String title;
    private String status;
    private InitiatorInfo initiator;
    private Integer participantCount;
    private LocalDateTime createdAt;

    @Data
    public static class InitiatorInfo {
        private String id;
        private String name;
    }
}
