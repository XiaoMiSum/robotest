package io.github.xiaomisum.robotest.model.dto.response.tcase;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class TestCaseDocumentRespDTO {

    private UUID id;
    private UUID projectId;
    private UUID moduleId;
    private String name;
    private Integer sortOrder;
    private Map<String, Object> layout;
    private LocalDateTime createdAt;
}