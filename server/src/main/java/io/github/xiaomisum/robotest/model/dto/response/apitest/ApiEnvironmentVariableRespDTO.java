package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

/** 环境变量导出 / 变量列表条目；value 明文（详细设计 3.1.9 / 3.3.4） */
@Data
public class ApiEnvironmentVariableRespDTO {

    private String id;

    private String name;

    private String value;

    private Boolean hasValue;

    private String description;

    private String sourceStepId;

    private String sourceReportId;
}
