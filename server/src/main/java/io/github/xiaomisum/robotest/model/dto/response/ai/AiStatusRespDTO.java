package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 业务端 AI 可用性（详设 3.2：GET /api/ai/status，登录即可）。
 */
@Data
public class AiStatusRespDTO {

    /** AI 总开关 */
    private Boolean enabled;

    /** 可用模型就绪（默认模型配置且启用） */
    private Boolean modelReady;

    /** 入口可见性：enabled && modelReady */
    private Boolean available;
}
