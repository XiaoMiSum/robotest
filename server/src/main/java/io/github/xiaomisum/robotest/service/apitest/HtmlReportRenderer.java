package io.github.xiaomisum.robotest.service.apitest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 独立 HTML 报告渲染：内联 CSS、零外部资源，可离线打开与打印（测试报告详细设计 4.3.2/6.1）。
 * 手写字符串模板而非 Thymeleaf，避免为单一导出功能引入模板引擎依赖。
 */
final class HtmlReportRenderer {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HtmlReportRenderer() {
    }

    static String render(String sceneName, String status, String environmentName, String executionMode,
            LocalDateTime createdAt, Map<String, Object> summary, List<Map<String, Object>> stepResults) {
        StringBuilder html = new StringBuilder(16 * 1024);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(escape(sceneName)).append(" - 测试报告</title>")
                .append("<style>")
                .append("*{box-sizing:border-box;margin:0;padding:0}")
                .append("body{font-family:-apple-system,'Segoe UI','Microsoft YaHei',sans-serif;color:#1f2937;background:#f5f6f8;padding:24px}")
                .append(".wrap{max-width:960px;margin:0 auto}")
                .append("h1{font-size:20px;margin-bottom:12px}")
                .append(".meta{color:#6b7280;font-size:13px;line-height:1.8;margin-bottom:20px}")
                .append(".cards{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:20px}")
                .append(".card{flex:1;min-width:120px;background:#fff;border-radius:8px;padding:16px;text-align:center}")
                .append(".card b{display:block;font-size:26px;margin-bottom:4px}")
                .append(".tag{display:inline-block;padding:1px 10px;border-radius:999px;font-size:12px;font-weight:600}")
                .append("@media print{body{background:#fff;padding:0}.step-body{display:block}}")
                .append("</style></head><body><div class=\"wrap\">");

        html.append("<h1>").append(escape(sceneName)).append(' ')
                .append(statusBadge(status)).append("</h1>");
        html.append("<p class=\"meta\">执行时间：").append(createdAt == null ? "-" : STAMP.format(createdAt))
                .append("　环境：").append(escape(environmentName))
                .append("　执行方式：").append("pipeline".equals(executionMode) ? "流水线" : "平台内执行")
                .append("</p>");

        long total = num(summary, "total");
        long passed = num(summary, "passed");
        long failed = num(summary, "failed");
        long skipped = num(summary, "skipped");
        html.append("<div class=\"cards\">")
                .append(card("总步骤", total, "#374151"))
                .append(card("通过", passed, "#16a34a"))
                .append(card("失败", failed, "#dc2626"))
                .append(card("跳过", skipped, "#9ca3af"))
                .append("</div>");
        double rate = total == 0 ? 0 : passed * 100.0 / total;
        html.append("<p class=\"meta\">通过率 ").append(String.format("%.1f", rate))
                .append("%　总耗时 ").append(num(summary, "durationMs")).append(" ms</p>");

        html.append("<h2 style=\"font-size:16px;margin-bottom:10px\">步骤结果</h2>");
        if (stepResults == null || stepResults.isEmpty()) {
            html.append("<p class=\"meta\">无步骤数据</p>");
        } else {
            int index = 1;
            for (Map<String, Object> step : stepResults) {
                renderStep(html, index++, step);
            }
        }

        html.append("</div><script>")
                // 详情默认折叠；导出场景常需打印全量，print 时由 CSS 强制展开
                .append("document.querySelectorAll('.step-head').forEach(function(h){")
                .append("h.addEventListener('click',function(){var b=h.nextElementSibling;")
                .append("b.style.display=b.style.display==='none'?'block':'none');});});")
                .append("</script></body></html>");
        return html.toString();
    }

    private static void renderStep(StringBuilder html, int index, Map<String, Object> step) {
        String status = str(step.get("status"));
        boolean collapsed = !"failed".equals(status) && !"error".equals(status);
        html.append("<div style=\"background:#fff;border-radius:8px;margin-bottom:10px;overflow:hidden\">");
        html.append("<div class=\"step-head\" style=\"display:flex;gap:12px;align-items:center;padding:12px 16px;cursor:pointer\">")
                .append("<b style=\"min-width:22px\">").append(index).append("</b>")
                .append("<span style=\"font-weight:600\">").append(escape(str(step.get("name")))).append("</span>")
                .append(statusBadge(status))
                .append("<span style=\"margin-left:auto;color:#6b7280;font-size:12px\">")
                .append(step.get("durationMs") == null ? "-" : step.get("durationMs") + " ms")
                .append("</span></div>");

        html.append("<div class=\"step-body\" style=\"padding:0 16px 14px;display:")
                .append(collapsed ? "none" : "block").append("\">");

        Object errorMessage = step.get("errorMessage");
        if (errorMessage != null) {
            html.append("<p style=\"color:#dc2626;font-size:13px;margin:8px 0\">")
                    .append(escape(str(errorMessage))).append("</p>");
        }

        Object request = step.get("request");
        if (request instanceof Map<?, ?> req) {
            html.append("<pre class=\"code\" style=\"background:#0f172a;color:#e2e8f0;border-radius:6px;")
                    .append("padding:10px;font-size:12px;overflow:auto;margin:8px 0\">")
                    .append(escape(req.get("method") + " " + req.get("url")))
                    .append("</pre>");
        }

        Object body = step.get("responseBody");
        if (body != null && !String.valueOf(body).isBlank()) {
            html.append(subhead("响应体"))
                    .append("<pre class=\"code\" style=\"background:#0f172a;color:#e2e8f0;border-radius:6px;")
                    .append("padding:10px;font-size:12px;overflow:auto;max-height:320px;margin:6px 0 10px\">")
                    .append(escape(truncate(String.valueOf(body), 20000)))
                    .append("</pre>");
        }

        Object validators = step.get("validators");
        if (validators instanceof List<?> list && !list.isEmpty()) {
            html.append(subhead("断言明细")).append("<table style=\"width:100%;border-collapse:collapse;font-size:13px\">")
                    .append("<tr style=\"color:#6b7280;text-align:left\"><th>目标</th><th>条件</th><th>期望值</th><th>实际值</th><th>结果</th></tr>");
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> validator)) {
                    continue;
                }
                html.append("<tr>")
                        .append(td(escape(str(validator.get("target")))))
                        .append(td(escape(str(validator.get("condition")))))
                        .append(td(escape(str(validator.get("expected")))))
                        // Ryze 无断言级实际值，占位说明（测试报告详细设计差异裁定 #2）
                        .append(td("-"))
                        .append(td("passed".equals(status) ? okTag("通过") : failTag("失败")))
                        .append("</tr>");
            }
            html.append("</table>");
        }
        html.append("</div></div>");
    }

    private static String subhead(String text) {
        return "<h3 style=\"font-size:13px;color:#374151;margin:10px 0 4px\">" + text + "</h3>";
    }

    private static String card(String label, long value, String color) {
        return "<div class=\"card\"><b style=\"color:" + color + "\">" + value + "</b>" + label + "</div>";
    }

    private static String td(String value) {
        return "<td style=\"padding:4px 8px 4px 0;border-top:1px solid #f3f4f6\">" + value + "</td>";
    }

    private static String statusBadge(String status) {
        return switch (status == null ? "" : status) {
            case "success", "passed" -> okTag("通过");
            case "failed" -> failTag("失败");
            case "error" -> failTag("异常");
            case "skipped" -> grayTag("跳过");
            default -> grayTag(escape(status));
        };
    }

    private static String okTag(String text) {
        return tag(text, "#dcfce7", "#166534");
    }

    private static String failTag(String text) {
        return tag(text, "#fee2e2", "#991b1b");
    }

    private static String grayTag(String text) {
        return tag(text, "#f3f4f6", "#4b5563");
    }

    private static String tag(String text, String bg, String fg) {
        return "<span class=\"tag\" style=\"background:" + bg + ";color:" + fg + "\">" + text + "</span>";
    }

    /** 用户内容（场景名/响应体等）入 HTML 前必须转义，防存储型 XSS */
    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "\n...[截断]";
    }

    private static long num(Map<String, Object> summary, String key) {
        if (summary == null || summary.get(key) == null) {
            return 0;
        }
        return ((Number) summary.get(key)).longValue();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
