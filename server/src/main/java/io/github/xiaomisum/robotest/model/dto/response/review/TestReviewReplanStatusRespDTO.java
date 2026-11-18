package io.github.xiaomisum.robotest.model.dto.response.review;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestReviewReplanStatusRespDTO {

    private String status;
    private String message;
    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
}
