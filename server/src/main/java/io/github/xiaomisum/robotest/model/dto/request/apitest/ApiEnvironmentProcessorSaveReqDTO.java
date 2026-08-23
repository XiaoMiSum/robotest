package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ApiEnvironmentProcessorSaveReqDTO {

    /** 处理器类别：preprocessor / postprocessor */
    @NotBlank(message = "处理器类别不能为空")
    private String processorType;

    @NotBlank(message = "处理器名称不能为空")
    private String name;

    private Map<String, Object> config;

    private Integer sortOrder;

    private Boolean enabled;
}
