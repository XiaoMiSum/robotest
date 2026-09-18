package io.github.xiaomisum.robotest.service.workspace.member;

/**
 * 拒绝加入的具体原因：Service 据此机械映射到 INVITATION_* 错误码，判定逻辑不重复出现在 Service
 */
public enum InvitationRejectReason {
    INVALID,
    REVOKED,
    EXPIRED,
    USE_EXHAUSTED
}