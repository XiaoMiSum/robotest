package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 环境详情聚合（GET /api/project/environments/:id 与导出共用同一结构）。
 *
 * <p>敏感变量值不输出明文：value 固定为掩码，hasValue 标识是否已配置；
 * 明文仅可经维护者 reveal 接口临时获取。</p>
 */
@Data
public class ApiEnvironmentDetailRespDTO {

    public static final String SENSITIVE_MASK = "******";

    private String id;
    private String name;
    private String description;
    private String scope;
    private Boolean isDefault;
    private Integer sortOrder;
    private List<HttpConfig> httpConfigs;
    private List<Variable> variables;
    private List<DataSource> dataSources;
    private List<Processor> processors;

    @Data
    public static class HttpConfig {

        private String id;
        private String name;
        private String refName;
        private String baseUrl;
        private String defaultMethod;
        private List<HeaderItem> headers;
        private Integer timeoutMs;
        private Integer connectTimeoutMs;
        private Boolean followRedirects;
        private Boolean verifySsl;
        private Boolean isDefault;
    }

    @Data
    public static class HeaderItem {

        private String key;
        private String value;
        private Boolean enabled;
    }

    @Data
    public static class Variable {

        private String id;
        private String name;
        /** sensitive 类型恒为掩码，明文不出接口 */
        private String value;
        /** 是否已配置值（敏感值前端据此显示「已配置」） */
        private Boolean hasValue;
        private String description;
        private String type;
    }

    @Data
    public static class DataSource {

        private String id;
        private String name;
        private String refName;
        private String driver;
        private String url;
        private Map<String, Object> connectionProperties;
        private Integer maxPoolSize;
    }

    @Data
    public static class Processor {

        private String id;
        private String processorType;
        private String name;
        private Map<String, Object> config;
        private Integer sortOrder;
        private Boolean enabled;
    }
}
