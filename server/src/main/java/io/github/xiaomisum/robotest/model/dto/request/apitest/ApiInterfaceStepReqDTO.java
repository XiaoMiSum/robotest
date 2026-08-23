package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建/更新公共步骤（接口管理详细设计 3.2.1/3.2.2）
 */
@Data
public class ApiInterfaceStepReqDTO {

    @NotBlank(message = "步骤名称不能为空")
    @Size(max = 200, message = "步骤名称长度不能超过 200")
    private String name;

    /** http / jdbc，V1.2 仅 http */
    @NotBlank(message = "步骤类型不能为空")
    private String stepType;

    private Integer sortOrder;

    private Boolean enabled;

    @NotNull(message = "请求配置不能为空")
    private Map<String, Object> requestConfig;

    private List<Map<String, Object>> processors;

    private List<Map<String, Object>> validators;

    private List<Map<String, Object>> extractors;
}
