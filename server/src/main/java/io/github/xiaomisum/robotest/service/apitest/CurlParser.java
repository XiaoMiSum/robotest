package io.github.xiaomisum.robotest.service.apitest;

import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * cURL 命令解析器（快速调试详细设计 4.2）。
 * 仅做文本解析，不执行命令；不支持的参数静默忽略。
 */
public final class CurlParser {

    private CurlParser() {
    }

    /** 解析结果：method/url/headers/bodyType/bodyContent */
    public record ParsedCurl(String method, String url,
                             List<Map<String, Object>> headers,
                             String bodyType, Object bodyContent) {
    }

    private static final Pattern LINE_CONTINUATION = Pattern.compile("\\\\\\r?\\n");
    private static final Pattern HEADER_SPLIT = Pattern.compile("\\s*:\\s*(.*)");
    /** 不支持但带取值的常见参数：跳过时须连同取值一起丢弃，防止取值被误判为 URL */
    private static final List<String> VALUE_FLAGS_IGNORED = List.of(
            "-x", "--proxy", "--proxy-user", "--noproxy",
            "-A", "--user-agent", "-e", "--referer",
            "--cacert", "--capath", "--cert", "--key", "--resolve",
            "-m", "--max-time", "--connect-timeout", "--retry");

    /**
     * 解析 cURL 命令；未找到 URL 时抛出 IllegalArgumentException。
     */
    public static ParsedCurl parse(String command) {
        List<String> tokens = tokenize(command == null ? "" : command);
        String method = null;
        String url = null;
        Map<String, String> headerMap = new LinkedHashMap<>();
        String dataType = null;
        StringBuilder dataValue = new StringBuilder();
        Map<String, Object> formData = new LinkedHashMap<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (VALUE_FLAGS_IGNORED.contains(token)) {
                i++;
                continue;
            }
            switch (token) {
                case "-X", "--request" -> {
                    if (i + 1 < tokens.size()) {
                        method = tokens.get(++i).toUpperCase();
                    }
                }
                case "-H", "--header" -> {
                    if (i + 1 < tokens.size()) {
                        collectHeader(headerMap, tokens.get(++i));
                    }
                }
                case "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode" -> {
                    if (i + 1 < tokens.size()) {
                        dataType = dataType == null ? "json" : dataType;
                        appendData(dataValue, tokens.get(++i));
                    }
                }
                case "-F", "--form" -> {
                    if (i + 1 < tokens.size()) {
                        dataType = "form";
                        collectForm(formData, tokens.get(++i));
                    }
                }
                case "-b", "--cookie" -> {
                    if (i + 1 < tokens.size()) {
                        headerMap.put("Cookie", tokens.get(++i));
                    }
                }
                default -> {
                    // 首个含协议或路径的位置参数视为目标 URL，其余未知参数静默跳过
                    if (!token.startsWith("-") && url == null && looksLikeUrl(token)) {
                        url = token;
                    }
                }
            }
        }

        if (url == null) {
            throw new IllegalArgumentException("cURL 命令中未找到请求 URL");
        }
        // curl 原生语义：携带请求体且未显式指定方法时默认 POST
        String resolvedMethod = method != null ? method
                : (!dataValue.isEmpty() || !formData.isEmpty() ? "POST" : "GET");
        return new ParsedCurl(resolvedMethod, url, toHeaderList(headerMap), dataType,
                resolveContent(dataType, dataValue.toString(), formData));
    }

    private static boolean looksLikeUrl(String token) {
        return token.contains("://") || token.startsWith("/");
    }

    private static void collectHeader(Map<String, String> headerMap, String raw) {
        Matcher matcher = HEADER_SPLIT.matcher(raw);
        if (matcher.find()) {
            headerMap.put(raw.substring(0, matcher.start()).trim(), matcher.group(1).trim());
        } else if (!raw.isBlank()) {
            headerMap.put(raw.trim(), "");
        }
    }

    private static void appendData(StringBuilder builder, String value) {
        if (!builder.isEmpty()) {
            builder.append('&');
        }
        builder.append(value);
    }

    private static void collectForm(Map<String, Object> formData, String raw) {
        int eq = raw.indexOf('=');
        if (eq > 0) {
            String key = raw.substring(0, eq);
            String value = raw.substring(eq + 1);
            // @file 语法指向本地文件，服务端执行禁止读取用户文件系统，丢弃该字段
            if (!value.startsWith("@")) {
                formData.put(key, value);
            }
        }
    }

    private static Object resolveContent(String type, String dataValue, Map<String, Object> formData) {
        if ("form".equals(type)) {
            return formData.isEmpty() ? null : formData;
        }
        if (dataValue.isEmpty()) {
            return null;
        }
        // JSON 解析成功返回结构化对象，失败降级为 raw 字符串，避免前端编辑器展示损坏的 JSON
        try {
            return JsonUtils.parseObject(dataValue, Object.class);
        } catch (Exception ex) {
            return dataValue;
        }
    }

    private static List<Map<String, Object>> toHeaderList(Map<String, String> headerMap) {
        List<Map<String, Object>> headers = new ArrayList<>();
        headerMap.forEach((key, value) -> headers.add(new LinkedHashMap<>() {{
            put("key", key);
            put("value", value);
            put("enabled", true);
        }}));
        return headers;
    }

    /**
     * 分词：合并行续接符后按 shell 引用规则切分。
     * 双引号内仅 \" 与 \\ 为转义，单引号内为字面量。
     */
    static List<String> tokenize(String command) {
        String normalized = LINE_CONTINUATION.matcher(command).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    current.append(c);
                }
            } else if (inDouble) {
                if (c == '\\' && i + 1 < normalized.length()
                        && (normalized.charAt(i + 1) == '"' || normalized.charAt(i + 1) == '\\')) {
                    current.append(normalized.charAt(++i));
                } else if (c == '"') {
                    inDouble = false;
                } else {
                    current.append(c);
                }
            } else if (c == '\'') {
                inSingle = true;
            } else if (c == '"') {
                inDouble = true;
            } else if (Character.isWhitespace(c)) {
                flush(tokens, current);
            } else if (c == '\\' && i + 1 < normalized.length()) {
                current.append(normalized.charAt(++i));
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }
}
