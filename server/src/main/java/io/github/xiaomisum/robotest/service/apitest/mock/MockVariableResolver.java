package io.github.xiaomisum.robotest.service.apitest.mock;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 响应变量解析器（Mock服务详细设计 6.2）。
 * 支持 ${uuid()}、${timestamp()}、${env:VAR}；未定义的 ${...} 保留原文。
 */
public class MockVariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private MockVariableResolver() {
    }

    public static String resolve(String input) {
        if (input == null || !input.contains("${")) {
            return input;
        }
        Matcher matcher = PLACEHOLDER.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolvePlaceholder(matcher.group(1))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolvePlaceholder(String expression) {
        if ("uuid()".equals(expression)) {
            return UUID.randomUUID().toString();
        }
        if ("timestamp()".equals(expression)) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (expression.startsWith("env:") && expression.length() > 4) {
            String value = System.getenv(expression.substring(4).trim());
            // 环境变量缺失时保留原始占位文本，便于调用方定位未就绪配置
            return value != null ? value : "${" + expression + "}";
        }
        return "${" + expression + "}";
    }

}
