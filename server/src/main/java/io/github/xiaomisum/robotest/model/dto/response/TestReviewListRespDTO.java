package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TestReviewListRespDTO {

    private UUID id;
    private String title;
    private String status;
    private InitiatorInfo initiator;
    private Integer participantCount;
    private LocalDateTime createdAt;

    @Data
    public static class InitiatorInfo {
        private UUID id;
        private String name;
    }
}
