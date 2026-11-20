package io.github.xiaomisum.robotest.model.dto.response.tcase;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TestCaseNodeTreeRespDTO {

    private UUID id;
    private UUID documentId;
    private UUID parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
    private Boolean aiGenerated;
    /** 关联需求 id（用例节点专有，供前端回显关联需求） */
    private UUID requirementId;
    private List<TestCaseNodeTreeRespDTO> children;
}
