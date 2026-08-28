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

    /** 引用名称，缺省按 http_N 生成（新增时）或沿用现值（编辑时） */
    private String refName;

    private String baseUrl;

    private List<ApiEnvironmentSaveReqDTO.HeaderItem> headers;
}