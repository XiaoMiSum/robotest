package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DebugRyzeConverterTest {

    private static Map<String, Object> header(String key, String value, boolean enabled) {
        return Map.of("key", key, "value", value, "enabled", enabled);
    }

    @Test
    void autoNameUsesMethodPlusPath() {
        assertThat(DebugRyzeConverter.autoName("post", "https://api.example.com/auth/login?next=x"))
                .isEqualTo("POST /auth/login");
        assertThat(DebugRyzeConverter.autoName("GET", "/users")).isEqualTo("GET /users");
    }

    @Test
    void absoluteUrlGoesToBaseUrlWithoutEnv() {
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("GET");
        req.setUrl("https://api.example.com/users");
        Map<String, Object> suite = DebugRyzeConverter.buildSuite(DebugRyzeConverter.EnvSnapshot.empty(), req);

        assertThat(suite.get("title")).isEqualTo("GET /users");
        Map<String, Object> child = firstChild(suite);
        assertThat(child.get("testclass")).isEqualTo("http");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) child.get("config");
        assertThat(config).containsEntry("method", "get")
                .containsEntry("base_url", "https://api.example.com/users");
    }

    @Test
    void relativeUrlPrefixesDefaultBaseUrlAndMergesHeaders() {
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("POST");
        req.setUrl("/auth/login");
        req.setHeaders(List.of(
                header("Authorization", "Bearer t", true),
                header("X-Drop", "ignored", false),
                header("Content-Type", "application/json", true)));
        var env = new DebugRyzeConverter.EnvSnapshot(
                "http://staging.local:8080",
                Map.of("Content-Type", "text/plain"),
                Map.of("token", "abc"),
                List.of(Map.of("testclass", "debug")),
                List.of());

        Map<String, Object> suite = DebugRyzeConverter.buildSuite(env, req);
        assertThat(suite.get("variables")).isEqualTo(Map.of("token", "abc"));
        assertThat(suite.get("preprocessors")).isEqualTo(List.of(Map.of("testclass", "debug")));

        Map<String, Object> child = firstChild(suite);
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) child.get("config");
        assertThat(config).containsEntry("base_url", "http://staging.local:8080/auth/login");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) config.get("headers");
        // 环境默认头在前，请求头同名覆盖，禁用项不参与
        assertThat(headers).containsEntry("Content-Type", "application/json")
                .containsEntry("Authorization", "Bearer t")
                .doesNotContainKey("X-Drop");
    }

    @Test
    void bodyTypeMapsToJsonBodyOrFormData() {
        ApiDebugExecuteReqDTO jsonReq = bodyReq("json", Map.of("a", 1));
        Map<String, Object> jsonConfig = configOf(jsonReq);
        assertThat(jsonConfig).containsEntry("body", Map.of("a", 1));

        ApiDebugExecuteReqDTO formReq = bodyReq("form", Map.of("k", "v"));
        assertThat(configOf(formReq)).containsEntry("data", Map.of("k", "v"));

        ApiDebugExecuteReqDTO rawReq = bodyReq("raw", "plain-text");
        assertThat(configOf(rawReq)).containsEntry("body", "plain-text");

        ApiDebugExecuteReqDTO noneReq = bodyReq("none", null);
        assertThat(configOf(noneReq)).doesNotContainKeys("body", "data");
    }

    @Test
    void queryParamListBecomesConfigQueryMap() {
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("GET");
        req.setUrl("/search");
        req.setParams(List.of(header("page", "2", true), header("size", "10", true)));
        Map<String, Object> config = configOf(req);
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) config.get("query");
        assertThat(query).containsEntry("page", "2").containsEntry("size", "10");
    }

    private ApiDebugExecuteReqDTO bodyReq(String type, Object content) {
        ApiDebugExecuteReqDTO req = new ApiDebugExecuteReqDTO();
        req.setMethod("POST");
        req.setUrl("/submit");
        ApiDebugExecuteReqDTO.Body body = new ApiDebugExecuteReqDTO.Body();
        body.setType(type);
        body.setContent(content);
        req.setBody(body);
        return req;
    }

    private Map<String, Object> configOf(ApiDebugExecuteReqDTO req) {
        Map<String, Object> child = firstChild(DebugRyzeConverter.buildSuite(
                DebugRyzeConverter.EnvSnapshot.empty(), req));
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) child.get("config");
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstChild(Map<String, Object> suite) {
        List<Object> children = ((List<Object>) suite.get("children"));
        return (Map<String, Object>) children.getFirst();
    }
}
