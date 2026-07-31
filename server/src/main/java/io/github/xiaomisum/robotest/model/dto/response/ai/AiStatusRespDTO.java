package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * AI 可用性状态（前端据此显隐全部 AI 入口）
 */
@Data
public class AiStatusRespDTO {

    private Boolean enabled;
    /** available / degraded / unavailable，enabled=false 时不返回 */
    private String semanticSearch;
}
