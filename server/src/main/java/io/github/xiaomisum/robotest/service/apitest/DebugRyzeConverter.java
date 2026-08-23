package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台调试请求模型 → Ryze TestSuite JSON 转换（基础设施详细设计 4.1.2）。
 * 仅生成 Ryze 标准 JSON 结构，执行交由 Ryze.start(Map) 完成。
 */
public final class DebugRyzeConverter {

    private DebugRyzeConverter() {
    }

    /**
     * 执行引用的环境快照：默认 HTTP 配置、变量明文、全局前置/后置处理器。
     * 处理器元素结构与 Ryze 元件一致（api_environment_processor.config 直接透传）。
     */
    public record EnvSnapshot(String baseUrl,
                              Map<String, Object> headers,
                              Map<String, Object> variables,
                              List<Map<String, Object>> preprocessors,
                              List<Map<String, Object>> postprocessors) {

        public static EnvSnapshot empty() {
            return new EnvSnapshot(null, Map.of(), Map.of(), List.of(), List.of());
        }
    }

    /** 记录命名规则：方法 + URL 路径（快速调试详细设计 4.1） */
    public static String autoName(String method, String url) {
        String path = extractPath(url);
        String suffix = path != null && !path.isBlank() ? path : "请求";
        return method.toUpperCase() + " " + suffix;
    }

    static String extractPath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String rest = url;
        int schemeEnd = rest.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = rest.indexOf('/', schemeEnd + 3);
            rest = pathStart < 0 ? "/" : rest.substring(pathStart);
        }
        int query = rest.indexOf('?');
        String path = query < 0 ? rest : rest.substring(0, query);
        return path.isBlank() ? "/" : path;
    }

    static boolean isAbsoluteUrl(String url) {
        return url != null && url.toLowerCase().matches("^https?://.*");
    }

    public static Map<String, Object> buildSuite(EnvSnapshot env, ApiDebugExecuteReqDTO req) {
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("title", autoName(req.getMethod(), req.getUrl()));
        if (!env.variables().isEmpty()) {
            suite.put("variables", env.variables());
        }
        if (!env.preprocessors().isEmpty()) {
            suite.put("preprocessors", env.preprocessors());
        }
        if (!env.postprocessors().isEmpty()) {
            suite.put("postprocessors", env.postprocessors());
        }
        suite.put("children", List.of(buildSampler(env, req)));
        return suite;
    }

    private static Map<String, Object> buildSampler(EnvSnapshot env, ApiDebugExecuteReqDTO req) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("method", req.getMethod().toLowerCase());
        // 绝对 URL 直接作为 base_url；相对路径拼接环境默认 baseUrl 后仍走 base_url，避免依赖 Ryze 多配置解析顺序
        if (isAbsoluteUrl(req.getUrl())) {
            config.put("base_url", req.getUrl());
        } else {
            config.put("base_url", (env.baseUrl() == null ? "" : env.baseUrl()) + req.getUrl());
        }
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.putAll(env.headers());
        mergeEnabledEntries(headers, req.getHeaders());
        if (!headers.isEmpty()) {
            config.put("headers", headers);
        }
        Map<String, Object> query = new LinkedHashMap<>();
        mergeEnabledEntries(query, req.getParams());
        if (!query.isEmpty()) {
            config.put("query", query);
        }
        applyBody(config, req.getBody());

        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("title", autoName(req.getMethod(), req.getUrl()));
        sampler.put("testclass", "http");
        sampler.put("config", config);
        return sampler;
    }

    private static void mergeEnabledEntries(Map<String, Object> target, List<Map<String, Object>> entries) {
        if (entries == null) {
            return;
        }
        for (Map<String, Object> entry : entries) {
            Object key = entry.get("key");
            if (key == null || Boolean.FALSE.equals(entry.get("enabled"))) {
                continue;
            }
            target.put(key.toString(), entry.getOrDefault("value", ""));
        }
    }

    private static void applyBody(Map<String, Object> config, ApiDebugExecuteReqDTO.Body body) {
        if (body == null || body.getContent() == null || "none".equalsIgnoreCase(body.getType())) {
            return;
        }
        switch (body.getType() == null ? "json" : body.getType().toLowerCase()) {
            case "form" -> config.put("data", body.getContent());
            case "raw" -> config.put("body", body.getContent().toString());
            case "binary" -> config.put("body", body.getContent().toString());
            default -> config.put("body", body.getContent());
        }
    }
}
