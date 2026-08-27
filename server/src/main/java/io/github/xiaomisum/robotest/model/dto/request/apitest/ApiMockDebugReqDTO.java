package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

import java.util.Map;

/**
 * Mock 调试请求（Mock服务详细设计 3.2.1）：模拟一次命中，不计入命中统计
 */
@Data
public class ApiMockDebugReqDTO {

    private Map<String, String> headers;

    /** 任意 JSON 请求体 */
    private Object body;

}
