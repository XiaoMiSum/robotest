package io.github.xiaomisum.robotest.service.apitest.mock;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 响应构建（Mock服务详细设计 4.1「跟随 API」与 6.2 变量解析）。
 * 自身未配置响应体且开启跟随 API 时回退关联接口的响应示例；响应头缺省补默认 Content-Type。
 */
public class MockResponseFactory {

    /** 构建结果：body 已完成变量解析，headers 为可写副本 */
    public record MockResponse(int status, Map<String, String> headers, String body, String bodyType) {
    }

    private MockResponseFactory() {
    }

    public static MockResponse build(ApiMockDefinition definition, Map<String, Object> interfaceResponseExample) {
        String bodyType = definition.getResponseBodyType() == null ? "json" : definition.getResponseBodyType();
        String body = MockVariableResolver.resolve(definition.getResponseBody());
        int status = definition.getResponseStatus() == null ? 200 : definition.getResponseStatus();
        Map<String, Object> rawHeaders = definition.getResponseHeaders();

        // 跟随 API：仅当自身未配置响应内容且开关打开时生效（需求 3.3）
        if ((body == null || body.isBlank()) && Boolean.TRUE.equals(definition.getFollowApi())
                && interfaceResponseExample != null && !interfaceResponseExample.isEmpty()) {
            Object exampleStatus = interfaceResponseExample.get("status");
            if (exampleStatus instanceof Number number) {
                status = number.intValue();
            }
            Object exampleHeaders = interfaceResponseExample.get("headers");
            if (exampleHeaders instanceof Map<?, ?> map && !map.isEmpty()) {
                rawHeaders = castHeaders(map);
            }
            Object exampleBody = interfaceResponseExample.get("body");
            if (exampleBody != null) {
                body = exampleBody instanceof String s ? s : String.valueOf(exampleBody);
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (rawHeaders != null) {
            rawHeaders.forEach((key, value) -> headers.put(key, value == null ? "" : String.valueOf(value)));
        }
        headers.putIfAbsent("Content-Type", defaultContentType(bodyType));
        return new MockResponse(status, headers, body == null ? "" : body, bodyType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> castHeaders(Map<?, ?> map) {
        return (Map<String, Object>) new LinkedHashMap<>((Map) map);
    }

    private static String defaultContentType(String bodyType) {
        return switch (bodyType) {
            case "xml" -> "application/xml";
            case "text" -> "text/plain";
            case "binary" -> "application/octet-stream";
            default -> "application/json";
        };
    }

}
