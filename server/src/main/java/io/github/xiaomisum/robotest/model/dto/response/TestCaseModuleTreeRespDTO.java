package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TestCaseModuleTreeRespDTO {

    private String id;
    private String parentId;
    private String type;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private List<TestCaseModuleTreeRespDTO> children;
}
