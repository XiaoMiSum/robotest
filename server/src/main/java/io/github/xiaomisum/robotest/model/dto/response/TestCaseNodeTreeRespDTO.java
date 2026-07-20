package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class TestCaseNodeTreeRespDTO {

    private String id;
    private String parentId;
    private String type;
    private String title;
    private String priority;
    private Integer sortOrder;
    private Integer version;
    private List<TestCaseNodeTreeRespDTO> children;
}
