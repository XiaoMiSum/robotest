package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 邀请链接列表项：仅返回脱敏预览，服务端计算有效状态，完整 token 按需获取。
 */
@Data
public class InvitationListRespDTO {

    private UUID id;
    private String tokenPreview;
    private String effectiveStatus;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
    private LocalDateTime createdAt;
}
