package io.github.xiaomisum.robotest.service.ai.review;

import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewConclusionRespDTO;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ReviewConclusionSupport 输出断言（06 §5.2）：keyFindings 允许空数组（无发现），
 * 但不得为 null 且条目不允许为空。
 */
class ReviewConclusionSupportTest {

    @Test
    void nullKeyFindings_throwsValidation() {
        AiReviewConclusionRespDTO out = new AiReviewConclusionRespDTO();
        out.setReason("结论");
        out.setKeyFindings(null);
        assertThrows(AiOutputValidator.OutputValidationException.class,
                () -> ReviewConclusionSupport.assertOutput(out));
    }

    @Test
    void emptyKeyFindings_accepted() {
        AiReviewConclusionRespDTO out = new AiReviewConclusionRespDTO();
        out.setReason("结论");
        out.setKeyFindings(List.of());
        assertDoesNotThrow(() -> ReviewConclusionSupport.assertOutput(out));
    }

    @Test
    void blankEntry_throwsValidation() {
        AiReviewConclusionRespDTO out = new AiReviewConclusionRespDTO();
        out.setReason("结论");
        out.setKeyFindings(List.of("有效发现", "  "));
        assertThrows(AiOutputValidator.OutputValidationException.class,
                () -> ReviewConclusionSupport.assertOutput(out));
    }
}