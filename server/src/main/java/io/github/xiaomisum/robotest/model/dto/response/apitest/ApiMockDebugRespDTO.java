package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.Map;

/** Mock 调试结果（Mock服务详细设计 3.2.1） */
@Data
public class ApiMockDebugRespDTO {

    private Integer status;
    private Map<String, Object> headers;
    /** JSON 类型时解析为对象，其余类型为原始字符串 */
    private Object body;
    private Long durationMs;

}
