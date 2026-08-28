package io.github.xiaomisum.robotest.service.apitest.imports;

import tools.jackson.databind.JsonNode;
import xyz.migoo.framework.common.util.JsonUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HAR 1.2 解析：entries 逐条转接口定义，始终创建新接口（无稳定 ID，详细设计 4.1）
 */
public class HarImportParser implements InterfaceImportParser {

    @Override
    public String sourceType() {
        return "har_entry";
    }

    @Override
    public boolean supports(String formatHint, String content) {
        if ("har".equals(formatHint)) {
            return true;
        }
        return content.contains("\"log\"") && content.contains("\"entries\"");
    }

    @Override
    public List<ImportedOperation> parse(String content) {
        JsonNode root;
        try {
            root = JsonUtils.toJSON(content);
        } catch (Exception exception) {
            throw new IllegalArgumentException("JSON 解析失败：" + exception.getMessage(), exception);
        }
        JsonNode entries = root == null ? null : root.path("log").path("entries");
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            throw new IllegalArgumentException("缺少 entries 定义");
        }
        List<ImportedOperation> operations = new ArrayList<>();
        int index = 0;
        for (JsonNode entry : entries) {
            index += 1;
            JsonNode request = entry.get("request");
            if (request == null) {
                continue;
            }
            operations.add(toOperation(request, index));
        }
        return operations;
    }

    private ImportedOperation toOperation(JsonNode request, int index) {
        String method = request.path("method").asText("GET").toUpperCase();
        String rawUrl = request.path("url").asText("");
        String path = extractPath(rawUrl);

        List<Map<String, Object>> headers = new ArrayList<>();
        for (JsonNode header : request.path("headers")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", header.path("name").asText());
            item.put("value", header.path("value").asText());
            item.put("enabled", true);
            headers.add(item);
        }

        List<Map<String, Object>> query = new ArrayList<>();
        for (JsonNode q : request.path("queryString")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", q.path("name").asText());
            item.put("value", q.path("value").asText());
            item.put("enabled", true);
            query.add(item);
        }

        Map<String, Object> body = null;
        JsonNode postData = request.get("postData");
        if (postData != null && !postData.isEmpty()) {
            body = new LinkedHashMap<>();
            String mimeType = postData.path("mimeType").asText("");
            String text = postData.path("text").asText("");
            if (mimeType.contains("json") && looksLikeJson(text)) {
                body.put("type", "json");
                body.put("content", readValueSafely(text));
            } else if (mimeType.contains("x-www-form-urlencoded")) {
                body.put("type", "form");
                body.put("content", parseForm(text));
            } else {
                body.put("type", "raw");
                body.put("content", text);
            }
        }

        return ImportedOperation.builder()
                .sourceId(method + ":" + path + "#" + index)
                .sourceName(method + " " + path)
                .method(method)
                .path(path)
                .headers(headers)
                .queryParams(query)
                .body(body)
                .build();
    }

    private String extractPath(String rawUrl) {
        int schemeEnd = rawUrl.indexOf("://");
        int start = schemeEnd < 0 ? 0 : rawUrl.indexOf('/', schemeEnd + 3);
        String rest = start < 0 ? "/" : rawUrl.substring(start);
        int query = rest.indexOf('?');
        rest = query < 0 ? rest : rest.substring(0, query);
        return URLDecoder.decode(rest.isEmpty() ? "/" : rest, StandardCharsets.UTF_8);
    }

    private Map<String, Object> parseForm(String text) {
        Map<String, Object> form = new LinkedHashMap<>();
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    /** JSON 文本解析失败时降级为原始字符串，与 cURL 解析降级口径一致 */
    private Object readValueSafely(String text) {
        try {
            return JsonUtils.parseObject(text, Object.class);
        } catch (Exception exception) {
            return text;
        }
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
