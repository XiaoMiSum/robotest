package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 连通性测试结果（6007 为业务结果语义，随 200 返回）
 */
@Data
public class AiConnectivityTestRespDTO {

    private Boolean ok;
    private Long latencyMs;
    private String detail;
}
