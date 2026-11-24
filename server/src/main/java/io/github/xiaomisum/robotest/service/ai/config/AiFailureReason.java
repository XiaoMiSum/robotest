package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 模型 / 向量调用失败原因（详设总册 4.5）：从异常链提取真实原因，跳过平台通用包装文案，
 * 脱敏凭据后按落点截断。错误码保持稳定，本工具只产出 msg / 日志文案，不改变异常语义。
 */
public final class AiFailureReason {

    /** 通用包装词表：仅标识失败类别，不携带可排查信息 */
    private static final Set<String> GENERIC_MESSAGES = Set.of(
            ErrorCodeConstants.AI_MODEL_CALL_FAILED.msg(),
            ErrorCodeConstants.AI_MODEL_TEST_FAILED.msg(),
            ErrorCodeConstants.AI_EMBEDDING_TEST_FAILED.msg(),
            "模型服务调用失败",
            "向量响应解析失败",
            "执行策略运行异常");

    /** 密钥 / 令牌形态：按出现顺序逐条替换为占位符，避免原文进入响应、落盘字段或日志 */
    private static final List<Pattern> SECRETS = List.of(
            Pattern.compile("(?i)\\bsk-[A-Za-z0-9_-]{4,}"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("(?i)\\b(api[_-]?key|access[_-]?token|secret|token|authorization)\\b\\s*[:=]\\s*[\"']?[^\"',;}\\s]+[\"']?"));

    private AiFailureReason() {
    }

    /** 提取并脱敏完整原因（无长度限制，供日志使用） */
    public static String of(Throwable error) {
        return of(error, Integer.MAX_VALUE);
    }

    /** 提取、脱敏并按落点长度上限截断（超出保留前缀） */
    public static String of(Throwable error, int maxLength) {
        if (error == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cursor = error;
        while (cursor != null && visited.add(cursor)) {
            String message = cursor.getMessage();
            if (message != null && !message.isBlank()) {
                String text = sanitize(message.trim());
                if (!text.isEmpty() && !GENERIC_MESSAGES.contains(text)) {
                    parts.add(text);
                }
            }
            cursor = cursor.getCause();
        }
        String reason;
        if (parts.isEmpty()) {
            // 原因链只有通用文案时退化为异常类型，至少标明真实失败类别而非包装文案
            reason = error.getClass().getSimpleName();
        } else {
            reason = parts.getFirst();
            if (parts.size() > 1) {
                String root = parts.getLast();
                if (!reason.contains(root)) {
                    reason = reason + ": " + root;
                }
            }
        }
        if (maxLength <= 0) {
            return "";
        }
        return reason.length() <= maxLength ? reason : reason.substring(0, maxLength);
    }

    private static String sanitize(String text) {
        String result = text;
        for (Pattern pattern : SECRETS) {
            result = pattern.matcher(result).replaceAll("***");
        }
        return result;
    }
}
