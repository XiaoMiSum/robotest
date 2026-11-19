package io.github.xiaomisum.robotest.service.ai.task.handler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps.FIELD_PRIORITY;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps.FIELD_TAGS;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps.FIELD_TITLE;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps.MAX_TAGS;
import static io.github.xiaomisum.robotest.service.ai.task.handler.CaseOptimizeOps.normalizeSuggested;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 用例优化操作指令取值归一单测（C8）：非法 / 空白建议丢弃，标签按上限截断。 */
class CaseOptimizeOpsTest {

    @Test
    void title_acceptsTextAndFirstListValue_rejectsBlankAndOversize() {
        assertEquals("输入邮箱和密码", normalizeSuggested(FIELD_TITLE, "  输入邮箱和密码  "));
        assertEquals("输入邮箱", normalizeSuggested(FIELD_TITLE, List.of("输入邮箱")));
        assertNull(normalizeSuggested(FIELD_TITLE, "   "));
        assertNull(normalizeSuggested(FIELD_TITLE, List.of()));
        assertNull(normalizeSuggested(FIELD_TITLE, "x".repeat(CaseOptimizeOps.MAX_TITLE + 1)));
    }

    @Test
    void priority_acceptsOnlyWhitelistedLevels() {
        assertEquals("P0", normalizeSuggested(FIELD_PRIORITY, "P0"));
        assertNull(normalizeSuggested(FIELD_PRIORITY, "high"));
        assertNull(normalizeSuggested(FIELD_PRIORITY, ""));
    }

    @Test
    void tags_truncateToLimitAndRejectEmpty() {
        List<String> raw = IntStream.range(0, MAX_TAGS + 3)
                .mapToObj(i -> "tag-" + i).toList();
        assertEquals(MAX_TAGS, ((List<?>) normalizeSuggested(FIELD_TAGS, raw)).size());
        assertNull(normalizeSuggested(FIELD_TAGS, List.of()));
        assertNull(normalizeSuggested(FIELD_TAGS, "登录"));
    }
}
