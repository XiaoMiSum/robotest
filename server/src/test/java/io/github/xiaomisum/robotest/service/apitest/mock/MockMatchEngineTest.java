package io.github.xiaomisum.robotest.service.apitest.mock;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiMockDefinition;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockMatchEngineTest {

    @Test
    void methodMismatchRejected() {
        ApiMockDefinition definition = definition("POST", "/api/users", null);
        assertFalse(MockMatchEngine.matches(definition, "GET", "/api/users", Map.of(), Map.of(), null));
    }

    @Test
    void exactPathMatch() {
        ApiMockDefinition definition = definition("GET", "/api/users/1", null);
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/users/1", Map.of(), Map.of(), null));
        assertFalse(MockMatchEngine.matches(definition, "GET", "/api/users/2", Map.of(), Map.of(), null));
    }

    @Test
    void wildcardPathMatches() {
        ApiMockDefinition definition = definition("GET", "/api/users/*/detail", null);
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/users/42/detail", Map.of(), Map.of(), null));
        assertFalse(MockMatchEngine.matches(definition, "GET", "/api/orders/42/detail", Map.of(), Map.of(), null));
        // * 语义为任意字符（含跨段），与常见 Mock 引擎一致
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/users/a/b/detail", Map.of(), Map.of(), null));
    }

    @Test
    void emptyRulesHitByPathOnly() {
        ApiMockDefinition definition = definition("GET", "/api/ping", List.of());
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/ping", Map.of(), Map.of(), null));
    }

    @Test
    void headerRuleWithRegexAndPlainValue() {
        // 正则合法时按整串匹配解释
        ApiMockDefinition regexRule = definition("GET", "/api/x",
                List.of(Map.of("type", "header", "name", "X-Version", "value", "v[0-9]+")));
        assertTrue(MockMatchEngine.matches(regexRule, "GET", "/api/x",
                Map.of("x-version", "v12"), Map.of(), null));
        assertFalse(MockMatchEngine.matches(regexRule, "GET", "/api/x",
                Map.of("x-version", "abc"), Map.of(), null));

        // 非法正则退化为字符串相等
        ApiMockDefinition plainRule = definition("GET", "/api/x",
                List.of(Map.of("type", "header", "name", "X-Tag", "value", "a[b")));
        assertTrue(MockMatchEngine.matches(plainRule, "GET", "/api/x",
                Map.of("x-tag", "a[b"), Map.of(), null));
        assertFalse(MockMatchEngine.matches(plainRule, "GET", "/api/x",
                Map.of(), Map.of(), null));
    }

    @Test
    void paramRuleMatchesQueryValue() {
        ApiMockDefinition definition = definition("GET", "/api/search",
                List.of(Map.of("type", "param", "name", "size", "value", "(large|small)")));
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/search",
                Map.of(), Map.of("size", "large"), null));
        assertFalse(MockMatchEngine.matches(definition, "GET", "/api/search",
                Map.of(), Map.of("size", "medium"), null));
    }

    @Test
    void bodyRuleWithJsonPathExtraction() {
        ApiMockDefinition definition = definition("POST", "/api/order",
                List.of(Map.of("type", "body", "name", "$.items[0].sku", "value", "A\\d+")));
        String body = "{\"items\":[{\"sku\":\"A100\"}]}";
        var bodyNode = JsonUtils.toJSON(body);
        assertTrue(MockMatchEngine.matches(definition, "POST", "/api/order", Map.of(), Map.of(), bodyNode));
        assertFalse(MockMatchEngine.matches(definition, "POST", "/api/order", Map.of(), Map.of(),
                JsonUtils.toJSON("{\"items\":[]}")));
        // body 规则在请求体缺失时不命中
        assertFalse(MockMatchEngine.matches(definition, "POST", "/api/order", Map.of(), Map.of(), null));
    }

    @Test
    void allRulesMustPass() {
        ApiMockDefinition definition = definition("GET", "/api/gate",
                List.of(Map.of("type", "header", "name", "H", "value", "1"),
                        Map.of("type", "param", "name", "P", "value", "2")));
        assertTrue(MockMatchEngine.matches(definition, "GET", "/api/gate",
                Map.of("h", "1"), Map.of("P", "2"), null));
        assertFalse(MockMatchEngine.matches(definition, "GET", "/api/gate",
                Map.of("h", "1"), Map.of(), null));
    }

    private static ApiMockDefinition definition(String method, String path, List<Map<String, Object>> rules) {
        ApiMockDefinition definition = new ApiMockDefinition();
        definition.setMethod(method);
        definition.setPath(path);
        definition.setMatchRules(rules);
        return definition;
    }

}
