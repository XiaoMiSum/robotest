package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新 Swagger URL 配置（定时任务详细设计 3.1.9）
 */
@Data
public class ApiSwaggerUrlSaveReqDTO {

    @NotBlank
    @Size(max = 200)
    private String name;

    /** URL 可达性与 SSRF 校验复用接口管理的 ImportSourceFetcher 规则 */
    @NotBlank
    @Size(max = 2000)
    private String url;

    @Pattern(regexp = "swagger|openapi", message = "格式仅支持 swagger / openapi")
    private String format;

}
