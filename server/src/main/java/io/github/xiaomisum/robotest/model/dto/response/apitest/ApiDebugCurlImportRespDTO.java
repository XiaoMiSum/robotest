package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * cURL 导入解析结果（快速调试详细设计 3.1.2），不执行请求。
 */
@Data
@Builder
public class ApiDebugCurlImportRespDTO {

    private String protocol;
    private String method;
    private String url;
    private List<Map<String, Object>> headers;
    private Body body;
    private List<Map<String, Object>> params;

    @Data
    @Builder
    public static class Body {
        /** none / json / form / raw */
        private String type;
        private Object content;
    }
}
