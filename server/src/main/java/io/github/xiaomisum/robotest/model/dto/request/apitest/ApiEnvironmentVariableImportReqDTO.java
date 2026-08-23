package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 3.3.3 批量导入环境变量：overwrite 控制重名覆盖或跳过 */
@Data
public class ApiEnvironmentVariableImportReqDTO {

    @Valid
    @NotEmpty(message = "变量列表不能为空")
    private List<ApiEnvironmentSaveReqDTO.Variable> variables;

    /** true 重名覆盖 / false 跳过（不新增） */
    private Boolean overwrite = false;
}
