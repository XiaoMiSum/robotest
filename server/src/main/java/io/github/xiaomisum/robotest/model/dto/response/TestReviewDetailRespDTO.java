package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestReviewDetailRespDTO {

    private UUID id;
    private String title;
    private String description;
    private String status;
    private InitiatorInfo initiator;
    private List<UUID> participantIds;
    private LocalDateTime createdAt;

    @Data
    public static class InitiatorInfo {
        private UUID id;
        private String name;
    }
}
