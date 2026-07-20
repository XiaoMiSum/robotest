package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

@Data
public class TestCaseDocumentNodesRespDTO {

    private TestCaseNodeTreeRespDTO node;
    private String layout;
}
