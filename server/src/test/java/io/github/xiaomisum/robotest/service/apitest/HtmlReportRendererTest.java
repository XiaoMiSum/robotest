package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTML 导出模板：内联 CSS/转义/折叠规则（测试报告详细设计 4.3.2、6.1） */
class HtmlReportRendererTest {

    @Test
    void rendersSummaryCardsAndStepRows() {
        String html = HtmlReportRenderer.render("登录链路", "success", "测试环境", "platform",
                LocalDateTime.of(2026, 8, 25, 10, 0), Map.of("total", 2, "passed", 2),
                List.of(Map.of("stepId", "s1", "name", "登录", "status", "success",
                        "durationMs", 320,
                        "validators", List.of(Map.of("target", "status_code", "condition", "equals", "expected", "200")))));

        assertTrue(html.contains("登录链路"));
        assertTrue(html.contains("通过率"));
        assertTrue(html.contains("status_code"));
        // 通过步骤默认折叠，失败步骤默认展开
        assertTrue(html.contains("display:none"));
    }

    @Test
    void escapesUserContentToPreventStoredXss() {
        String html = HtmlReportRenderer.render("<script>alert(1)</script>", "failed", "测试环境", "platform",
                LocalDateTime.now(), Map.of("total", 1), List.of());

        assertFalse(html.contains("<script>alert(1)"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void failedStepExpandsByDefaultAndShowsError() {
        String html = HtmlReportRenderer.render("下单链路", "partial", null, "pipeline",
                LocalDateTime.now(), Map.of("total", 2, "passed", 1, "failed", 1),
                List.of(Map.of("stepId", "s2", "name", "支付", "status", "error", "errorMessage", "连接超时")));

        assertTrue(html.contains("连接超时"));
        assertTrue(html.contains("异常"));
    }

    @Test
    void emptyStepResultsShowsPlaceholder() {
        String html = HtmlReportRenderer.render("空场景", "success", null, "platform",
                LocalDateTime.now(), Map.of("total", 0), List.of());

        assertTrue(html.contains("无步骤数据"));
    }
}
