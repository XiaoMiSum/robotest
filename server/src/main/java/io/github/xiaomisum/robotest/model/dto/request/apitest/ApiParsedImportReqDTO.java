package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 前端解析后的接口导入请求（接口管理详细设计 3.4.1，cURL 导入）
 */
@Data
public class ApiParsedImportReqDTO {

    @NotEmpty(message = "解析后的接口列表不能为空")
    private List<Operation> operations;

    @Data
    public static class Operation {

        private String name;
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
}