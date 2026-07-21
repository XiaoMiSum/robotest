package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TestCaseModuleTreeRespDTO {

    private UUID id;
    private UUID parentId;
    private String type;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private List<TestCaseModuleTreeRespDTO> children;
}
