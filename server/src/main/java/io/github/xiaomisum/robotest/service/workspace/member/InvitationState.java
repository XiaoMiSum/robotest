package io.github.xiaomisum.robotest.service.workspace.member;

/**
 * 邀请三态：ACTIVE/REVOKED 为落库状态，EXPIRED 为运行期派生态（expiresAt < now，C5 免迁移）
 */
public enum InvitationState {
    ACTIVE,
    REVOKED,
    EXPIRED
}