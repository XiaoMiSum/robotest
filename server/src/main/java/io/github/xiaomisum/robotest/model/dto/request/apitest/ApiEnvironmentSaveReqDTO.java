package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 环境（含 HTTP 配置/变量/数据源/处理器）聚合保存请求（POST/PUT /api/project/environments）。
 *
 * <p>
 * 更新为全量语义：子资源以请求列表整批替换（详细设计 3.1.3/3.1.4）；
 * 敏感变量值加密存储；HTTP 配置缺省时自动生成默认配置。
 * </p>
 */
@Data
public class ApiEnvironmentSaveReqDTO {

    @NotBlank(message = "环境名称不能为空")
    @Size(max = 100, message = "环境名称不能超过 100 字符")
    private String name;

    @Size(max = 500, message = "环境描述不能超过 500 字符")
    private String description;

    /** 是否设为项目默认环境（项目内唯一，置 true 时其余环境自动取消默认） */
    private Boolean isDefault = false;

    private Integer sortOrder = 0;

    /** 缺省时服务端自动生成一条默认 HTTP 配置 */
    @Valid
    private List<HttpConfig> httpConfigs;

    @Valid
    private List<Variable> variables;

    @Valid
    private List<DataSource> dataSources;

    @Valid
    private List<Processor> processors;

    @Data
    public static class HttpConfig {

        @NotBlank(message = "HTTP 配置名称不能为空")
        @Size(max = 100, message = "HTTP 配置名称不能超过 100 字符")
        private String name;

        /** 引用名称（Ryze ref_name），缺省时按 http_N 自动生成 */
        @Size(max = 100, message = "引用名称不能超过 100 字符")
        private String refName;

        @NotBlank(message = "Base URL 不能为空")
        @Size(max = 2000, message = "Base URL 不能超过 2000 字符")
        private String baseUrl;

        @Size(max = 10, message = "默认方法不合法")
        private String defaultMethod;

        @Valid
        private List<HeaderItem> headers;

        private Integer timeoutMs = 30000;

        private Integer connectTimeoutMs = 10000;

        private Boolean followRedirects = true;

        private Boolean verifySsl = true;

        /** 每个环境有且仅有一个默认 HTTP 配置，均未标记时取首项 */
        private Boolean isDefault = false;
    }

    @Data
    public static class HeaderItem {

        @NotBlank(message = "请求头 Key 不能为空")
        private String key;

        private String value;

        private Boolean enabled = true;
    }

    @Data
    public static class Variable {

        @NotBlank(message = "变量名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "变量名仅允许字母、数字、下划线")
        @Size(max = 100, message = "变量名不能超过 100 字符")
        private String name;

        private String value;

        @Size(max = 500, message = "变量描述不能超过 500 字符")
        private String description;

        /** text / number / sensitive */
        private String type = "text";
    }

    @Data
    public static class DataSource {

        @NotBlank(message = "数据源名称不能为空")
        @Size(max = 100, message = "数据源名称不能超过 100 字符")
        private String name;

        @NotBlank(message = "引用名称不能为空")
        @Size(max = 100, message = "引用名称不能超过 100 字符")
        private String refName;

        @NotBlank(message = "JDBC 驱动类名不能为空")
        @Size(max = 100, message = "JDBC 驱动类名不能超过 100 字符")
        private String driver;

        @NotBlank(message = "JDBC 连接 URL 不能为空")
        @Size(max = 500, message = "JDBC 连接 URL 不能超过 500 字符")
        private String url;

        private Map<String, Object> connectionProperties;

        private Integer maxPoolSize = 5;
    }

    @Data
    public static class Processor {

        /** preprocessor / postprocessor，范围校验在 Service 层执行 */
        @NotBlank(message = "处理器类型不能为空")
        private String processorType;

        @NotBlank(message = "处理器名称不能为空")
        @Size(max = 100, message = "处理器名称不能超过 100 字符")
        private String name;

        private Map<String, Object> config;

        private Integer sortOrder = 0;

        private Boolean enabled = true;
    }
}
