package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * HTTP 配置独立新增/编辑（3.1.12）：与处理器子资源一致，按配置粒度即时保存，
 * 不再依赖聚合 updateEnvironment 整表替换。
 */
@Data
public class ApiEnvironmentHttpConfigSaveReqDTO {

    @NotBlank(message = "配置名称不能为空")
    private String name;

    @NotBlank(message = "引用名不能为空")
    private String refName;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    private List<ApiEnvironmentSaveReqDTO.HeaderItem> headers;
}