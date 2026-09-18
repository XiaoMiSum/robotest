package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;

import java.util.List;

/**
 * 评审结论判定器（06 §5.2）：按关联用例 lastMark 统计确定性判定 verdict 并生成依据说明。
 * 纯函数不依赖 DB/AI，随 ReviewConclusionEvent 在 completeReview 发布，供 AI 域生成结论使用。
 */
public final class ReviewConclusionEvaluator {

    private ReviewConclusionEvaluator() {
    }

    public record Conclusion(ReviewConclusionVerdict verdict, String reason) {
    }

    /**
     * 判定规则（优先级从高到低）：无关联用例 → INCONCLUSIVE；
     * 存在 FAIL → FAIL；存在未评审（PENDING）→ INCONCLUSIVE；其余 → PASS。
     */
    public static Conclusion evaluate(List<TestReviewNodeSnapshot> caseNodes) {
        long total = 0;
        long fail = 0;
        long pending = 0;
        if (caseNodes != null) {
            total = caseNodes.size();
            for (TestReviewNodeSnapshot node : caseNodes) {
                String mark = node == null || node.getLastMark() == null ? null : node.getLastMark();
                if (mark == null || mark.isBlank()) {
                    pending++;
                } else if (Constants.ReviewMark.FAIL.equals(mark)) {
                    fail++;
                }
            }
        }
        return of(total, fail, pending);
    }

    static Conclusion of(long total, long fail, long pending) {
        ReviewConclusionVerdict verdict;
        String reason;
        if (total == 0) {
            verdict = ReviewConclusionVerdict.INCONCLUSIVE;
            reason = "评审未关联测试用例，无法得出有效结论。";
        } else if (fail > 0) {
            verdict = ReviewConclusionVerdict.FAIL;
            reason = "存在 " + fail + " 个不通过用例，评审结论为不通过。";
        } else if (pending > 0) {
            verdict = ReviewConclusionVerdict.INCONCLUSIVE;
            reason = "仍有 " + pending + " 个用例未完成评审，结论待定。";
        } else {
            verdict = ReviewConclusionVerdict.PASS;
            reason = "全部用例已评审且无不通过项，评审通过。";
        }
        return new Conclusion(verdict, reason);
    }
}