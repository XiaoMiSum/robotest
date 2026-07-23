package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用例列表项响应 DTO
 */
@Data
public class TestCaseCaseListRespDTO {

    private UUID id;
    private String title;
    private String type;
    private String priority;
    private UUID documentId;
    private String documentName;
    private Integer sortOrder;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
