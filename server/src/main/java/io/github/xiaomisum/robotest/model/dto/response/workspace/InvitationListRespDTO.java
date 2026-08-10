package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 邀请链接列表项：不含 token，避免敏感凭据随列表接口下发
 * （token 仅在创建接口返回，供创建者立即展示链接）。
 */
@Data
public class InvitationListRespDTO {

    private UUID id;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
    private LocalDateTime createdAt;
}
