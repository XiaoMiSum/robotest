package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 批量更新场景变量（测试场景详细设计 3.5.1），全量覆盖；空列表=清空
 */
@Data
public class ApiSceneVariableBatchReqDTO {

    @Valid
    private List<Variable> variables;

    @Data
    public static class Variable {

        private String name;

        private String value;

        private String description;

    }

}
