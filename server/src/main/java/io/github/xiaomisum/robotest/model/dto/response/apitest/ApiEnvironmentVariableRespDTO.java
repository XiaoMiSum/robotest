package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** 环境变量导出 / 明文查看条目；导出时敏感值恒为掩码 */
@Data
public class ApiEnvironmentVariableRespDTO {

    private String id;

    private String name;

    private String value;

    private Boolean hasValue;

    private String type;

    private String description;

    private String sourceStepId;

    private String sourceReportId;
}
