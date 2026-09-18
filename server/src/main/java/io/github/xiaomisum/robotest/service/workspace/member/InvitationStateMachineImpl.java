package io.github.xiaomisum.robotest.service.workspace.member;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 三态判定实现：EXPIRED 为派生态（只看 expiresAt，不落库）。
 * 拒绝优先级与重构前 validateAndGetInvitation 一致：REVOKED > EXPIRED > USE_EXHAUSTED，行为保持。
 */
@Service
public class InvitationStateMachineImpl implements InvitationStateMachine {

    @Override
    public InvitationState stateOf(WorkspaceInvitation invitation, LocalDateTime now) {
        if (invitation == null) {
            return null;
        }
        if (!Constants.Status.ACTIVE.equals(invitation.getStatus())) {
            return InvitationState.REVOKED;
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(now)) {
            return InvitationState.EXPIRED;
        }
        return InvitationState.ACTIVE;
    }

    @Override
    public InvitationDecision decision(WorkspaceInvitation invitation, LocalDateTime now) {
        if (invitation == null) {
            // 邀请不存在：verify 公开端点返回 valid=false，join 路径映射 INVITATION_INVALID
            return InvitationDecision.rejected(InvitationRejectReason.INVALID);
        }
        InvitationState state = stateOf(invitation, now);
        if (state == InvitationState.REVOKED) {
            return InvitationDecision.rejected(InvitationRejectReason.REVOKED);
        }
        if (state == InvitationState.EXPIRED) {
            return InvitationDecision.rejected(InvitationRejectReason.EXPIRED);
        }
        if (invitation.getMaxUses() != null && invitation.getUseCount() >= invitation.getMaxUses()) {
            return InvitationDecision.rejected(InvitationRejectReason.USE_EXHAUSTED);
        }
        return InvitationDecision.allowed();
    }
}