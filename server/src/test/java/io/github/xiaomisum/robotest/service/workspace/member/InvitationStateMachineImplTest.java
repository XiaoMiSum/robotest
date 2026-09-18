package io.github.xiaomisum.robotest.service.workspace.member;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvitationStateMachineImplTest {

    private final InvitationStateMachine machine = new InvitationStateMachineImpl();

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 18, 12, 0);

    private WorkspaceInvitation invitation(String status, LocalDateTime expiresAt, Integer maxUses, Integer useCount) {
        WorkspaceInvitation inv = new WorkspaceInvitation();
        inv.setStatus(status);
        inv.setExpiresAt(expiresAt);
        inv.setMaxUses(maxUses);
        inv.setUseCount(useCount);
        return inv;
    }

    // ========== stateOf ==========

    @Test
    void stateOf_absent_isNull() {
        assertNull(machine.stateOf(null, now));
    }

    @Test
    void stateOf_active_whenNoExpiry() {
        assertEquals(InvitationState.ACTIVE, machine.stateOf(invitation(Constants.Status.ACTIVE, null, null, 0), now));
    }

    @Test
    void stateOf_active_whenNotYetExpired() {
        assertEquals(InvitationState.ACTIVE, machine.stateOf(invitation(Constants.Status.ACTIVE, now.plusDays(1), null, 0), now));
    }

    @Test
    void stateOf_active_atExpiryBoundary_stillActive() {
        // isBefore 为严格小于，恰等于 expiresAt 时仍视为有效，与重构前语义一致
        assertEquals(InvitationState.ACTIVE, machine.stateOf(invitation(Constants.Status.ACTIVE, now, null, 0), now));
    }

    @Test
    void stateOf_expired_whenPastExpiry() {
        assertEquals(InvitationState.EXPIRED, machine.stateOf(invitation(Constants.Status.ACTIVE, now.minusMinutes(1), null, 0), now));
    }

    @Test
    void stateOf_revoked_forAnyNonActiveStatus() {
        assertEquals(InvitationState.REVOKED, machine.stateOf(invitation(Constants.Status.REVOKED, null, null, 0), now));
        assertEquals(InvitationState.REVOKED, machine.stateOf(invitation("dirty", now.minusDays(1), null, 0), now));
    }

    // ========== decision ==========

    @Test
    void decision_absent_rejectsInvalid() {
        InvitationDecision decision = machine.decision(null, now);
        assertEquals(InvitationRejectReason.INVALID, decision.reason());
        assertFalse(decision.joinable());
    }

    @Test
    void decision_revoked_rejectsRevoked() {
        assertRejected(InvitationRejectReason.REVOKED, invitation(Constants.Status.REVOKED, now.plusDays(1), null, 0));
    }

    @Test
    void decision_expired_rejectsExpired() {
        assertRejected(InvitationRejectReason.EXPIRED, invitation(Constants.Status.ACTIVE, now.minusMinutes(1), null, 0));
    }

    @Test
    void decision_revokedTakesPriorityOverExpired() {
        assertRejected(InvitationRejectReason.REVOKED, invitation(Constants.Status.REVOKED, now.minusDays(1), null, 0));
    }

    @Test
    void decision_expiredTakesPriorityOverUsage() {
        assertRejected(InvitationRejectReason.EXPIRED, invitation(Constants.Status.ACTIVE, now.minusMinutes(1), 1, 5));
    }

    @Test
    void decision_maxUsesReached_rejectsExhausted() {
        assertRejected(InvitationRejectReason.USE_EXHAUSTED, invitation(Constants.Status.ACTIVE, now.plusDays(1), 5, 5));
        assertRejected(InvitationRejectReason.USE_EXHAUSTED, invitation(Constants.Status.ACTIVE, now.plusDays(1), 5, 6));
    }

    @Test
    void decision_unlimited_joinable() {
        InvitationDecision decision = machine.decision(invitation(Constants.Status.ACTIVE, null, null, 999), now);
        assertTrue(decision.joinable());
        assertNull(decision.reason());
    }

    @Test
    void decision_underLimit_joinable() {
        InvitationDecision decision = machine.decision(invitation(Constants.Status.ACTIVE, now.plusDays(1), 5, 4), now);
        assertTrue(decision.joinable());
        assertNull(decision.reason());
    }

    private void assertRejected(InvitationRejectReason expected, WorkspaceInvitation inv) {
        InvitationDecision decision = machine.decision(inv, now);
        assertEquals(expected, decision.reason());
        assertFalse(decision.joinable());
    }
}