package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class TestCaseDocumentNodesRespDTO {

    private TestCaseNodeTreeRespDTO node;
    // Map 由 Jackson 输出标准 JSON 对象；此前用 Map.toString() 产物非法，前端无法解析
    private Map<String, Object> layout;
}
