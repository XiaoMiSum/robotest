package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock 创建/更新请求（Mock服务详细设计 3.1.3/3.1.5）
 */
@Data
public class ApiMockSaveReqDTO {

    private UUID interfaceId;

    @NotBlank(message = "Mock 名称不能为空")
    @Size(max = 200, message = "Mock 名称不能超过 200 字符")
    private String name;

    @Size(max = 500, message = "Mock 描述不能超过 500 字符")
    private String description;

    @NotBlank(message = "HTTP 方法不能为空")
    private String method;

    @NotBlank(message = "请求路径不能为空")
    @Pattern(regexp = "^/.*", message = "请求路径必须以 / 开头")
    @Size(max = 500, message = "请求路径不能超过 500 字符")
    private String path;

    /** 缺省时取同路径同方法组内最大值 + 1 */
    private Integer priority;

    /** [{type, name, value}]，type: header/param/body */
    private List<Map<String, Object>> matchRules;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    private Boolean followApi;

    @NotNull(message = "响应状态码不能为空")
    @Min(value = 100, message = "状态码必须在 100-599 之间")
    @Max(value = 599, message = "状态码必须在 100-599 之间")
    private Integer responseStatus;

    private Map<String, Object> responseHeaders;

    private String responseBodyType;

    private String responseBody;

    @Min(value = 0, message = "延迟毫秒数不能为负数")
    private Integer delayMs;

}
