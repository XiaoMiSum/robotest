package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BugWorkflowImplTest {

    private final BugWorkflow workflow = new BugWorkflowImpl();

    private Bug bug(BugStatus status) {
        Bug b = new Bug();
        b.setStatus(status.getCode());
        return b;
    }

    @Test
    void activeState() {
        assertTransition(BugStatus.ACTIVE, BugStatus.RESOLVED);
        assertTransition(BugStatus.ACTIVE, BugStatus.REJECTED);
        assertIllegal(BugStatus.ACTIVE, BugStatus.CLOSED);
        assertIllegal(BugStatus.ACTIVE, BugStatus.ACTIVE);
    }

    @Test
    void resolvedState() {
        assertTransition(BugStatus.RESOLVED, BugStatus.CLOSED);
        assertTransition(BugStatus.RESOLVED, BugStatus.ACTIVE);
        assertIllegal(BugStatus.RESOLVED, BugStatus.REJECTED);
        assertIllegal(BugStatus.RESOLVED, BugStatus.RESOLVED);
    }

    @Test
    void rejectedState() {
        assertTransition(BugStatus.REJECTED, BugStatus.CLOSED);
        assertTransition(BugStatus.REJECTED, BugStatus.ACTIVE);
        assertIllegal(BugStatus.REJECTED, BugStatus.RESOLVED);
        assertIllegal(BugStatus.REJECTED, BugStatus.REJECTED);
    }

    @Test
    void closedState_onlyReopenAllowed() {
        assertTransition(BugStatus.CLOSED, BugStatus.ACTIVE);
        assertIllegal(BugStatus.CLOSED, BugStatus.RESOLVED);
        assertIllegal(BugStatus.CLOSED, BugStatus.REJECTED);
        assertIllegal(BugStatus.CLOSED, BugStatus.CLOSED);
    }

    @Test
    void unknownStatusRejected() {
        Bug dirty = new Bug();
        dirty.setStatus("dirty_status");
        // 未知状态对齐重构前 isValidTransition 的 getOrDefault 语义：不可流动
        assertFalse(workflow.can(null, BugStatus.ACTIVE));
        assertThrows(ServiceException.class, () -> workflow.transition(dirty, BugAction.REOPEN));
        assertThrows(ServiceException.class, () -> workflow.assertTransition(null, BugStatus.ACTIVE));
    }

    @Test
    void assertTransition_throwingOnlyOnIllegal() {
        assertDoesNotThrow(() -> workflow.assertTransition(BugStatus.RESOLVED, BugStatus.CLOSED));
        assertThrows(ServiceException.class, () -> workflow.assertTransition(BugStatus.ACTIVE, BugStatus.CLOSED));
    }

    private void assertTransition(BugStatus from, BugStatus to) {
        BugAction action = requireAction(to);
        assertEquals(to, workflow.transition(bug(from), action));
        assertTrue(workflow.can(from, to));
        assertDoesNotThrow(() -> workflow.assertTransition(from, to));
    }

    private void assertIllegal(BugStatus from, BugStatus to) {
        BugAction action = requireAction(to);
        assertFalse(workflow.can(from, to));
        assertThrows(ServiceException.class, () -> workflow.transition(bug(from), action));
        assertThrows(ServiceException.class, () -> workflow.assertTransition(from, to));
    }

    private BugAction requireAction(BugStatus to) {
        BugAction action = BugAction.of(to);
        assertNotNull(action);
        return action;
    }
}