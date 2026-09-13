package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewWorkflowImplTest {

    private final ReviewWorkflow workflow = new ReviewWorkflowImpl();

    private TestReview review(ReviewStatus status) {
        TestReview r = new TestReview();
        r.setStatus(status.getCode());
        return r;
    }

    @Test
    void newState() {
        assertTransition(ReviewStatus.NEW, ReviewEvent.SUBMIT_RECORD, ReviewStatus.IN_PROGRESS);
        assertTransition(ReviewStatus.NEW, ReviewEvent.UPDATE_CASES, ReviewStatus.NEW);
        assertTransition(ReviewStatus.NEW, ReviewEvent.SYNC, ReviewStatus.NEW);
        assertTransition(ReviewStatus.NEW, ReviewEvent.COMPLETE, ReviewStatus.COMPLETED);
        assertTransition(ReviewStatus.NEW, ReviewEvent.DELETE, ReviewStatus.NEW);
    }

    @Test
    void inProgressState() {
        assertTransition(ReviewStatus.IN_PROGRESS, ReviewEvent.SUBMIT_RECORD, ReviewStatus.IN_PROGRESS);
        assertTransition(ReviewStatus.IN_PROGRESS, ReviewEvent.UPDATE_CASES, ReviewStatus.IN_PROGRESS);
        assertTransition(ReviewStatus.IN_PROGRESS, ReviewEvent.SYNC, ReviewStatus.IN_PROGRESS);
        assertTransition(ReviewStatus.IN_PROGRESS, ReviewEvent.COMPLETE, ReviewStatus.COMPLETED);
        assertTransition(ReviewStatus.IN_PROGRESS, ReviewEvent.DELETE, ReviewStatus.IN_PROGRESS);
    }

    @Test
    void completedState_idempotentCompleteAndDeleteAllowed() {
        assertEquals(ReviewStatus.COMPLETED, workflow.transition(review(ReviewStatus.COMPLETED), ReviewEvent.COMPLETE));
        assertEquals(ReviewStatus.COMPLETED, workflow.transition(review(ReviewStatus.COMPLETED), ReviewEvent.DELETE));
        assertTrue(workflow.can(review(ReviewStatus.COMPLETED), ReviewEvent.COMPLETE));
        assertTrue(workflow.can(review(ReviewStatus.COMPLETED), ReviewEvent.DELETE));
    }

    @Test
    void completedState_illegalTransitionsThrow() {
        assertIllegal(ReviewEvent.SUBMIT_RECORD);
        assertIllegal(ReviewEvent.UPDATE_CASES);
        assertIllegal(ReviewEvent.SYNC);
    }

    @Test
    void unknownStatusTreatedAsNew() {
        TestReview review = new TestReview();
        review.setStatus("unknown_state");
        assertEquals(ReviewStatus.IN_PROGRESS, workflow.transition(review, ReviewEvent.SUBMIT_RECORD));
        assertTrue(workflow.can(review, ReviewEvent.SUBMIT_RECORD));
    }

    @Test
    void assertTransition_throwsOnIllegal() {
        assertThrows(ServiceException.class,
                () -> workflow.assertTransition(review(ReviewStatus.COMPLETED), ReviewEvent.SYNC));
        assertDoesNotThrow(() -> workflow.assertTransition(review(ReviewStatus.NEW), ReviewEvent.SYNC));
    }

    private void assertTransition(ReviewStatus from, ReviewEvent event, ReviewStatus to) {
        assertEquals(to, workflow.transition(review(from), event));
        assertTrue(workflow.can(review(from), event));
    }

    private void assertIllegal(ReviewEvent event) {
        assertFalse(workflow.can(review(ReviewStatus.COMPLETED), event));
        assertThrows(ServiceException.class, () -> workflow.transition(review(ReviewStatus.COMPLETED), event));
        assertThrows(ServiceException.class, () -> workflow.assertTransition(review(ReviewStatus.COMPLETED), event));
    }
}