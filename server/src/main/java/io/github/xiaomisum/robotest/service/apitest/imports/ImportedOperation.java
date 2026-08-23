package io.github.xiaomisum.robotest.service.apitest.imports;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 导入解析产物：一条接口定义的规范化中间模型（接口管理详细设计 4.1）
 */
@Data
@Builder
public class ImportedOperation {

    /** 源标识（operationId / item id / HAR 序号等），用于增量导入匹配 */
    private String sourceId;
    private String sourceName;
    private String method;
    private String path;
    private String description;
    /** [{key, value, enabled}] */
    private List<Map<String, Object>> headers;
    /** [{key, value, enabled}] */
    private List<Map<String, Object>> queryParams;
    /** {type, content} */
    private Map<String, Object> body;
}
