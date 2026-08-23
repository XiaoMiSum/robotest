package io.github.xiaomisum.robotest.service.apitest.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Postman Collection v2 解析：递归 items 收集 request 条目（接口管理详细设计 4.1）
 */
public class PostmanImportParser implements InterfaceImportParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String sourceType() {
        return "postman_item";
    }

    @Override
    public boolean supports(String formatHint, String content) {
        if ("postman".equals(formatHint)) {
            return true;
        }
        return content.stripLeading().startsWith("{") && content.contains("\"info\"") && content.contains("\"item\"");
    }

    @Override
    public List<ImportedOperation> parse(String content) {
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (Exception exception) {
            throw new IllegalArgumentException("JSON 解析失败：" + exception.getMessage(), exception);
        }
        if (root == null || !root.has("item")) {
            throw new IllegalArgumentException("缺少 items 定义");
        }
        List<ImportedOperation> operations = new ArrayList<>();
        collect(root.get("item"), operations);
        return operations;
    }

    private void collect(JsonNode items, List<ImportedOperation> out) {
        for (JsonNode item : items) {
            if (item.has("item")) {
                collect(item.get("item"), out);
                continue;
            }
            JsonNode request = item.get("request");
            if (request == null) {
                continue;
            }
            out.add(toOperation(item, request));
        }
    }

    private ImportedOperation toOperation(JsonNode item, JsonNode request) {
        JsonNode urlNode = request.get("url");
        String rawUrl = urlNode == null ? "" : urlNode.isTextual() ? urlNode.asText() : urlNode.path("raw").asText("");
        String path = extractPath(rawUrl);
        String method = request.path("method").asText("GET").toUpperCase();

        Map<String, Object> body = null;
        JsonNode bodyNode = request.get("body");
        if (bodyNode != null && !bodyNode.isEmpty()) {
            body = new LinkedHashMap<>();
            String mode = bodyNode.path("mode").asText("");
            if ("raw".equals(mode)) {
                String raw = bodyNode.path("raw").asText("");
                body.put("type", looksLikeJson(raw) ? "json" : "raw");
                body.put("content", parseJsonSafely(raw));
            } else if ("urlencoded".equals(mode)) {
                body.put("type", "form");
                body.put("content", formPairs(bodyNode.get("urlencoded")));
            } else if ("formdata".equals(mode)) {
                body.put("type", "form");
                body.put("content", formPairs(bodyNode.get("formdata")));
            }
        }

        List<Map<String, Object>> headers = new ArrayList<>();
        for (JsonNode header : request.path("header")) {
            headers.add(kv(header.path("key").asText(), header.path("value").asText()));
        }

        List<Map<String, Object>> query = new ArrayList<>();
        if (urlNode != null && urlNode.has("query")) {
            for (JsonNode q : urlNode.get("query")) {
                Map<String, Object> entry = kv(q.path("key").asText(), q.path("value").asText());
                query.add(entry);
            }
        }

        return ImportedOperation.builder()
                .sourceId(item.path("id").asText(method + ":" + path))
                .sourceName(item.path("name").asText(method + " " + path))
                .method(method)
                .path(path)
                .description(request.path("description").asText(null))
                .headers(headers)
                .queryParams(query)
                .body(body)
                .build();
    }

    private List<Map<String, Object>> formPairs(JsonNode array) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        if (array == null) {
            return pairs;
        }
        for (JsonNode node : array) {
            pairs.add(kv(node.path("key").asText(), node.path("value").asText()));
        }
        return pairs;
    }

    private Map<String, Object> kv(String key, String value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("value", value);
        map.put("enabled", true);
        return map;
    }

    private String extractPath(String rawUrl) {
        int schemeEnd = rawUrl.indexOf("://");
        int start = schemeEnd < 0 ? 0 : rawUrl.indexOf('/', schemeEnd + 3);
        String rest = start < 0 ? "/" : rawUrl.substring(start);
        int query = rest.indexOf('?');
        rest = query < 0 ? rest : rest.substring(0, query);
        return URLDecoder.decode(rest.isEmpty() ? "/" : rest, StandardCharsets.UTF_8);
    }

    private Object parseJsonSafely(String text) {
        try {
            return MAPPER.readValue(text, Object.class);
        } catch (Exception exception) {
            return text;
        }
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
