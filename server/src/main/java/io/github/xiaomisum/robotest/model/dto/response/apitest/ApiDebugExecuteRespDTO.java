package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 快速调试执行结果（详细设计 3.1.1）
 */
@Data
@Builder
public class ApiDebugExecuteRespDTO {

    private String debugRecordId;
    /** success / failed / error */
    private String status;
    private Integer responseStatus;
    private Map<String, Object> responseHeaders;
    /** 响应体（JSON 可解析时为结构化对象，否则为原始字符串） */
    private Object responseBody;
    private Integer durationMs;
    private Integer size;
    private String errorMessage;
}
