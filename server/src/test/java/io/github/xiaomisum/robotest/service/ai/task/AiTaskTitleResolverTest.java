package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTaskTitleResolverTest {

    @Test
    void resolve_withSourceTitle_usesTypeLabelAndSource() {
        assertEquals("AI 评审 - 登录模块评审", AiTaskTitleResolver.resolve(
                Constants.AiTaskType.REVIEW_CASE_REVIEW, "登录模块评审"));
        assertEquals("需求导入 - 需求规格V2.md", AiTaskTitleResolver.resolve(
                Constants.AiTaskType.REQUIREMENT_IMPORT, "  需求规格V2.md  "));
    }

    @Test
    void resolve_withoutSourceTitle_keepsTypeLabelOnly() {
        assertEquals("用例优化", AiTaskTitleResolver.resolve(Constants.AiTaskType.CASE_OPTIMIZE, "   "));
    }

    @Test
    void resolve_unknownType_fallsBackToRawType() {
        assertEquals("future_type - 来源", AiTaskTitleResolver.resolve("future_type", "来源"));
    }

    @Test
    void resolve_overLimit_isFittedTo200() {
        String title = AiTaskTitleResolver.resolve(Constants.AiTaskType.PLAN_ORDER, "标".repeat(220));
        assertEquals(200, title.length());
        assertEquals("计划排序 - " + "标".repeat(200 - "计划排序 - ".length() - 1) + "…", title);
    }
}
