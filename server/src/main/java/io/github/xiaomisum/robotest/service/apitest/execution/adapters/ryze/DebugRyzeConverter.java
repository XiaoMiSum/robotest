package io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

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
        List<Map<String, Object>> configElements = SceneRyzeConverter.buildConfigureElements(env);
        if (!configElements.isEmpty()) {
            suite.put("configelements", configElements);
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
        // 绝对 URL 步骤级覆盖 base_url；相对路径映射为 path，经 ref 继承环境默认 http 配置的 base_url/headers
        if (isAbsoluteUrl(req.getUrl())) {
            config.put("base_url", req.getUrl());
        } else {
            config.put("path", req.getUrl());
        }
        String ref = SceneRyzeConverter.defaultHttpRef(env);
        if (!ref.isBlank()) {
            config.put("ref", ref);
        }
        Map<String, Object> headers = new LinkedHashMap<>();
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
            // Ryze http 取样器 data 接收 map
            case "form" -> config.put("data", toFormData(body.getContent()));
            case "raw", "binary" -> config.put("body", body.getContent().toString());
            default -> config.put("body", body.getContent());
        }
    }

    /** 表单负载统一为 {key: value} map（Ryze http data 语义）；入参为原始 key-value 列表 */
    private static Map<String, Object> toFormData(Object content) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (content instanceof Map<?, ?> map) {
            map.forEach((k, v) -> data.put(k.toString(), v == null ? "" : v));
        } else if (content instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> row) {
                    Object key = row.get("key");
                    if (key == null || Boolean.FALSE.equals(row.get("enabled"))) {
                        continue;
                    }
                    Object value = row.get("value");
                    data.put(key.toString(), value == null ? "" : value);
                }
            }
        }
        return data;
    }
}
