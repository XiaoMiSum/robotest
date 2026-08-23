package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 3.3.2 从执行结果添加变量：携带来源步骤/报告便于追溯 */
@Data
public class ApiEnvironmentVariableCreateReqDTO {

    @NotBlank(message = "变量名不能为空")
    private String name;

    private String value;

    /** text / number / sensitive，缺省 text */
    private String type;

    private String description;

    private String sourceStepId;

    private String sourceReportId;
}
