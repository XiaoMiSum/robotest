package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** 接口级变量条目（接口管理详细设计 3.3.1） */
@Data
@Builder
public class ApiInterfaceVariableRespDTO {

    private UUID id;
    private String name;
    private String defaultValue;
    private String description;
    private Boolean required;
    private Integer sortOrder;
}
