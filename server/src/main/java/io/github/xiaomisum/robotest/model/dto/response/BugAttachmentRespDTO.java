package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BugAttachmentRespDTO {

    private UUID id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private UUID uploaderId;
    private String uploaderName;
    private LocalDateTime createdAt;
}
