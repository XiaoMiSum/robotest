package io.github.xiaomisum.robotest.service.workspace.member;

/**
 * 邀请判定结果：reason 为 null 表示可加入（joinable），否则携带拒绝原因
 */
public record InvitationDecision(InvitationRejectReason reason) {

    public static InvitationDecision allowed() {
        return new InvitationDecision(null);
    }

    public static InvitationDecision rejected(InvitationRejectReason reason) {
        return new InvitationDecision(reason);
    }

    public boolean joinable() {
        return reason == null;
    }
}