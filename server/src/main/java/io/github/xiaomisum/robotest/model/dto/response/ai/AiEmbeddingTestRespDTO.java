package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 向量 API 连通性测试结果（POST /api/ai/embedding/test，详设 3.4）。
 */
@Data
public class AiEmbeddingTestRespDTO {

    private Boolean success;

    /** 实测返回向量维度（与配置不一致时提示核对） */
    private Integer dimensions;

    private Integer latencyMs;

    private String msg;
}
