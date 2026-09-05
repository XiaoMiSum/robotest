package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 环境详情聚合（GET /api/project/environments/:id 与导出共用同一结构）。
 *
 * <p>变量值明文输出（详细设计 3.1.9）；hasValue 标识是否已配置。</p>
 */
@Data
public class ApiEnvironmentDetailRespDTO {

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
        private Boolean isDefault;
        private List<HeaderItem> headers;
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
        /** 变量取值明文（详细设计 3.1.9） */
        private String value;
        /** 是否已配置值 */
        private Boolean hasValue;
        private String description;
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
        private Boolean isDefault;
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
