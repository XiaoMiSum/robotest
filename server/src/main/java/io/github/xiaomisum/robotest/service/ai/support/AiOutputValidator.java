package io.github.xiaomisum.robotest.service.ai.support;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 结构化输出防线 —— JSON 宽容提取 + Jackson 强类型绑定 + Bean Validation 程序化触发。
 *
 * <p>与人工输入走同一套校验体系（SRS 3.1）；校验失败消息用于 LLM 带错重试与用户提示，
 * 重试编排由 AiGatewayService 负责。</p>
 */
@Component
public class AiOutputValidator {

    private static final Pattern THINK_PATTERN = Pattern.compile("<think>[\\s\\S]*?</think>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE_PATTERN = Pattern.compile("```[a-zA-Z]*\\s*|```");

    private final Validator validator;

    public AiOutputValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * 剥离 think 段与 Markdown 代码围栏（结构化解析前统一处理）
     */
    public static String stripNoise(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = THINK_PATTERN.matcher(raw).replaceAll("");
        cleaned = FENCE_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    /**
     * 截取首个完整 JSON 对象/数组（括号配对，忽略字符串字面量内的括号），无则返回 null
     */
    public static String extractJson(String raw) {
        String cleaned = stripNoise(raw);
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        int start = -1;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        char open = cleaned.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = inString;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 提取 → 绑定 → Bean Validation → 自定义结构断言；失败抛 {@link OutputValidationException}，
     * 消息为中文校验说明（供带错重试拼接）
     */
    public <T> T parseAndValidate(String raw, Class<T> resultType, Consumer<T> extraAssertion) {
        String json = extractJson(raw);
        if (json == null) {
            throw new OutputValidationException("输出中未找到完整的 JSON 结构");
        }
        T result;
        try {
            result = JsonUtils.parseObject(json, resultType);
        } catch (Exception e) {
            throw new OutputValidationException("JSON 无法绑定为目标结构：" + e.getMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining("；"));
            throw new OutputValidationException(message);
        }
        if (extraAssertion != null) {
            try {
                extraAssertion.accept(result);
            } catch (OutputValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new OutputValidationException(e.getMessage());
            }
        }
        return result;
    }

    /**
     * 结构化校验失败（消息用于 LLM 带错重试与管理端 schema_invalid 统计）
     */
    public static class OutputValidationException extends RuntimeException {

        public OutputValidationException(String message) {
            super(message);
        }
    }
}
