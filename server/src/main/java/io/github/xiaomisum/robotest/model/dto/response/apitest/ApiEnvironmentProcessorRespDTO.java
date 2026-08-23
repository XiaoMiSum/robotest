package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.Map;

@Data
public class ApiEnvironmentProcessorRespDTO {

    private String id;

    /** 处理器类别：preprocessor / postprocessor */
    private String processorType;

    private String name;

    private Map<String, Object> config;

    private Integer sortOrder;

    private Boolean enabled;
}
