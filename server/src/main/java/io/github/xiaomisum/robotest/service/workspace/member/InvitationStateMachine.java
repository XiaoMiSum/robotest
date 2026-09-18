package io.github.xiaomisum.robotest.service.workspace.member;

import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;

import java.time.LocalDateTime;

/**
 * 邀请状态机唯一裁决入口（02 §3.1.2）：收敛散在 Service 的布尔/分原因判定为纯函数
 */
public interface InvitationStateMachine {

    /**
     * 当前状态：REVOKED（非 active）优先判定，其次 EXPIRED（派生），null 表示邀请不存在
     */
    InvitationState stateOf(WorkspaceInvitation invitation, LocalDateTime now);

    /**
     * 联合判定：状态 + useCount/maxUses 守卫组合出 joinable 或拒绝原因
     */
    InvitationDecision decision(WorkspaceInvitation invitation, LocalDateTime now);
}