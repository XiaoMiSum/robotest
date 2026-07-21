package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TestCaseNodeTreeRespDTO {

    private UUID id;
    private UUID parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
    private List<TestCaseNodeTreeRespDTO> children;
}
