package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 3.3.1 批量更新环境变量：全量替换该环境的变量列表 */
@Data
public class ApiEnvironmentVariableBatchReqDTO {

    @Valid
    @NotEmpty(message = "变量列表不能为空")
    private List<ApiEnvironmentSaveReqDTO.Variable> variables;
}
