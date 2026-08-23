package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 调试记录恢复响应（详细设计 3.1.7）：完整请求快照 + 响应结果
 */
@Data
@Builder
public class ApiDebugRestoreRespDTO {

    private String debugRecordId;
    private Snapshot request;
    private Response response;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class Snapshot {
        private String protocol;
        private String method;
        private String url;
        private List<Map<String, Object>> headers;
        private Map<String, Object> body;
        private List<Map<String, Object>> params;
    }

    @Data
    @Builder
    public static class Response {
        private Integer statusCode;
        private Map<String, Object> headers;
        /** 响应体（JSON 可解析时为结构化对象，否则为原始字符串） */
        private Object body;
        private Integer elapsed;
        private Integer size;
    }
}
