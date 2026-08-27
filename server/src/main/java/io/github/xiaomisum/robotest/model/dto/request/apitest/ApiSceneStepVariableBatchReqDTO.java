package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 批量更新步骤变量（测试场景详细设计 3.4.2），全量覆盖；手动更新的 source 置 custom
 */
@Data
public class ApiSceneStepVariableBatchReqDTO {

    @Valid
    private List<Variable> variables;

    @Data
    public static class Variable {

        private String name;

        private String value;

        private String description;

    }

}
