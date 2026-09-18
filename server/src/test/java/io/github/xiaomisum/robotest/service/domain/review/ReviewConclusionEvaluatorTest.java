package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReviewConclusionEvaluator 判定口径（06 §5.2）：无用例→INCONCLUSIVE；有 FAIL→FAIL；
 * 有未评审(PENDING)→INCONCLUSIVE；全部评审无 FAIL→PASS；FAIL 优先级高于 PENDING。
 */
class ReviewConclusionEvaluatorTest {

    private static TestReviewNodeSnapshot caseNode(String mark) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(UUID.randomUUID());
        node.setType(Constants.NodeType.CASE);
        node.setLastMark(mark);
        return node;
    }

    @Test
    void emptyAssociatedCases_verdictInconclusive() {
        assertEquals(ReviewConclusionVerdict.INCONCLUSIVE,
                ReviewConclusionEvaluator.evaluate(List.of()).verdict());
        assertEquals("评审未关联测试用例，无法得出有效结论。",
                ReviewConclusionEvaluator.evaluate(List.of()).reason());
    }

    @Test
    void nullNodes_treatedAsEmpty() {
        assertEquals(ReviewConclusionVerdict.INCONCLUSIVE,
                ReviewConclusionEvaluator.evaluate(null).verdict());
    }

    @Test
    void anyFail_verdictFail() {
        List<TestReviewNodeSnapshot> nodes = new ArrayList<>();
        nodes.add(caseNode(Constants.ReviewMark.PASS));
        nodes.add(caseNode(Constants.ReviewMark.FAIL));
        nodes.add(caseNode(Constants.ReviewMark.PASS));
        assertEquals(ReviewConclusionVerdict.FAIL, ReviewConclusionEvaluator.evaluate(nodes).verdict());
        assertEquals("存在 1 个不通过用例，评审结论为不通过。",
                ReviewConclusionEvaluator.evaluate(nodes).reason());
    }

    @Test
    void pendingRemaining_verdictInconclusive() {
        List<TestReviewNodeSnapshot> nodes = new ArrayList<>();
        nodes.add(caseNode(Constants.ReviewMark.PASS));
        nodes.add(caseNode(null));
        assertEquals(ReviewConclusionVerdict.INCONCLUSIVE,
                ReviewConclusionEvaluator.evaluate(nodes).verdict());
    }

    @Test
    void allReviewedWithoutFail_verdictPass() {
        List<TestReviewNodeSnapshot> nodes = new ArrayList<>();
        nodes.add(caseNode(Constants.ReviewMark.PASS));
        nodes.add(caseNode(Constants.ReviewMark.PASS));
        assertEquals(ReviewConclusionVerdict.PASS, ReviewConclusionEvaluator.evaluate(nodes).verdict());
        assertEquals("全部用例已评审且无不通过项，评审通过。",
                ReviewConclusionEvaluator.evaluate(nodes).reason());
    }

    @Test
    void failOverridesPending_verdictFail() {
        List<TestReviewNodeSnapshot> nodes = new ArrayList<>();
        nodes.add(caseNode(null));
        nodes.add(caseNode(Constants.ReviewMark.FAIL));
        assertEquals(ReviewConclusionVerdict.FAIL, ReviewConclusionEvaluator.evaluate(nodes).verdict());
    }
}