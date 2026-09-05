package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台模型 → Ryze TestSuite 转换表逐行断言（测试场景详细设计 4.2/4.3，
 * 以 Ryze 6.1.0 实测 KW 注册名为准）
 */
class SceneRyzeConverterTest {

    private static DebugRyzeConverter.EnvSnapshot env() {
        Map<String, Object> headers = new java.util.LinkedHashMap<>();
        headers.put("X-Env-Header", "env-value");
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("envVar", "v1");
        variables.put("host", "resolved.example.com");
        return new DebugRyzeConverter.EnvSnapshot(
                "http://env.example.com", headers, variables, List.of(), List.of());
    }

    private static SceneRyzeConverter.StepSpec spec(String url) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", url);
        return new SceneRyzeConverter.StepSpec("step-1", config, List.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSampler(Map<String, Object> suite) {
        return (Map<String, Object>) ((List<Object>) suite.get("children")).get(0);
    }

    // ========== 套件组装 ==========

    @Test
    void suiteMergesVariablesWithScenePriorityOverEnv() {
        Map<String, Object> suiteVars = SceneRyzeConverter.buildSuiteVariables(env(),
                List.of(Map.of("name", "envVar", "value", "scene-wins")));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite("s", env(),
                suiteVars, List.of(), List.of(spec("/a")));
        Map<?, ?> variables = (Map<?, ?>) suite.get("variables");
        assertEquals("scene-wins", variables.get("envVar"));
        assertEquals("s", suite.get("title"));
    }

    @Test
    void relativeUrlJoinsEnvBaseUrlAbsoluteUrlWins() {
        Map<String, Object> joined = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/api/a"))));
        assertEquals("http://env.example.com/api/a", ((Map<?, ?>) joined.get("config")).get("base_url"));

        Map<String, Object> absolute = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("http://other.example.com/x"))));
        assertEquals("http://other.example.com/x", ((Map<?, ?>) absolute.get("config")).get("base_url"));
    }

    @Test
    void stepHeadersOverrideEnvHeadersAndDisabledSkipped() {
        SceneRyzeConverter.StepSpec step = new SceneRyzeConverter.StepSpec("step",
                new java.util.LinkedHashMap<>(Map.of(
                        "method", "GET", "url", "/a",
                        "headers", List.of(
                                Map.of("key", "X-Env-Header", "value", "step-wins", "enabled", true),
                                Map.of("key", "X-Off", "value", "no", "enabled", false),
                                Map.of("key", "X-Only-Step", "value", "yes")))),
                List.of(), List.of());
        Map<String, Object> sampler = firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(), List.of(step)));
        Map<?, ?> headers = (Map<?, ?>) ((Map<?, ?>) sampler.get("config")).get("headers");
        assertEquals("step-wins", headers.get("X-Env-Header"));
        assertEquals("yes", headers.get("X-Only-Step"));
        assertFalse(headers.containsKey("X-Off"));
    }

    @Test
    void stepVariablesPlacedAtSamplerLevel() {
        Map<String, Object> stepVars = Map.of("token", "abc123");
        Map<String, Object> sampler = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of("envVar", "v1"), List.of(stepVars),
                List.of(spec("/a"))));
        Map<?, ?> samplerVariables = (Map<?, ?>) sampler.get("variables");
        assertEquals("abc123", samplerVariables.get("token"));
    }

    @Test
    void bodyNormalizationByType() {
        Map<String, Object> base = new java.util.LinkedHashMap<>(Map.of("method", "POST"));

        Map<String, Object> none = new java.util.LinkedHashMap<>(base);
        none.put("url", "/n");
        none.put("body", Map.of("type", "none", "content", "x"));
        Map<?, ?> noneConfig = (Map<?, ?>) firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(),
                        List.of(new SceneRyzeConverter.StepSpec("t", none, List.of(), List.of())))).get("config");
        assertFalse(noneConfig.containsKey("body"));

        Map<String, Object> form = new java.util.LinkedHashMap<>(base);
        form.put("url", "/f");
        form.put("body", Map.of("type", "form", "content",
                List.of(Map.of("key", "a", "value", "1", "enabled", true),
                        Map.of("key", "skip", "value", "x", "enabled", false))));
        Map<?, ?> formConfig = (Map<?, ?>) firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(),
                        List.of(new SceneRyzeConverter.StepSpec("t", form, List.of(), List.of())))).get("config");
        assertEquals(Map.of("a", "1"), formConfig.get("data"));
        assertFalse(formConfig.containsKey("body"));
    }

    // ========== 验证器转换表 ==========

    @Test
    void statusCodeValidatorMapsToStatusElement() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "status_code", "condition", "equals", "expected", 200, "name", "码")));
        assertEquals(1, elements.size());
        Map<String, Object> element = elements.get(0);
        assertEquals("status", element.get("testclass"));
        // expected 经平台层字符串化，引擎侧按数值解析
        assertEquals("200", element.get("expected"));
        assertEquals("equals", element.get("rule"));
        assertNull(element.get("field"));
    }

    @Test
    void jsonFieldValidatorMapsToJsonElementWithExpressionField() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(
                Map.of("target", "json_field", "expression", "$.data.id",
                        "condition", "greater_or_equal", "expected", 1),
                Map.of("target", "json_field", "condition", "equals", "expected", "x")));
        assertEquals("json", elements.get(0).get("testclass"));
        assertEquals("$.data.id", elements.get(0).get("field"));
        assertEquals("gte", elements.get(0).get("rule"));
        // 缺省 condition 回退 equals
        assertEquals("equals", elements.get(1).get("rule"));
    }

    @Test
    void responseHeaderValidatorPrefixesHeadersPath() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "response_header", "expression", "Content-Type",
                "condition", "contains", "expected", "json")));
        assertEquals("http", elements.get(0).get("testclass"));
        assertEquals("headers.Content-Type", elements.get(0).get("field"));
        assertEquals("contains", elements.get(0).get("rule"));
    }

    @Test
    void responseBodyStartsWithAnchorsToRegex() {
        List<Map<String, Object>> starts = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "response_body", "condition", "starts_with", "expected", "{\"ok\"")));
        assertEquals("result", starts.get(0).get("testclass"));
        assertEquals("regex", starts.get(0).get("rule"));
        assertTrue(starts.get(0).get("expected").toString().startsWith("^\\Q{\"ok\""));

        List<Map<String, Object>> ends = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "response_body", "condition", "ends_with", "expected", "}")));
        assertTrue(ends.get(0).get("expected").toString().endsWith("\\E$"));

        List<Map<String, Object>> plain = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "response_body", "condition", "not_contains", "expected", "error")));
        assertEquals("not_contains", plain.get(0).get("rule"));
    }

    @Test
    void regexValidatorTargetsWholeResultBody() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "regex", "expression", "\\d+", "condition", "matches_regex")));
        assertEquals("result", elements.get(0).get("testclass"));
        assertEquals("\\d+", elements.get(0).get("expected"));
        assertEquals("regex", elements.get(0).get("rule"));
    }

    @Test
    void disabledValidatorSkippedAndUnknownTargetRejected() {
        assertEquals(0, SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "status_code", "enabled", false))).size());
        assertThrows(IllegalArgumentException.class,
                () -> SceneRyzeConverter.convertValidators(List.of(Map.of("target", "xpath"))));
        assertThrows(IllegalArgumentException.class,
                () -> SceneRyzeConverter.convertValidators(List.of(Map.of(
                        "target", "status_code", "condition", "starts_with"))));
    }

    @Test
    void compareRuleTableCoversEngineRegisteredMatchers() {
        assertEquals("gt", SceneRyzeConverter.compareRule("greater_than"));
        assertEquals("lt", SceneRyzeConverter.compareRule("less_than"));
        assertEquals("lte", SceneRyzeConverter.compareRule("less_or_equal"));
        assertEquals("not_equals", SceneRyzeConverter.compareRule("not_equals"));
        assertEquals("not_contains", SceneRyzeConverter.compareRule("not_contains"));
    }

    // ========== 提取器转换表 ==========

    @Test
    void extractorTableMapsSourcesToEngineElements() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertExtractors(List.of(
                Map.of("source", "json_field", "expression", "$.token", "variableName", "token"),
                Map.of("source", "response_header", "expression", "Request-Id", "variableName", "rid"),
                Map.of("source", "regex", "expression", "(\\d+)", "variableName", "num"),
                Map.of("source", "full_body", "variableName", "raw"),
                Map.of("source", "json_field", "expression", "$.off", "variableName", "skip",
                        "enabled", false)));
        assertEquals(4, elements.size());
        assertEquals("json", elements.get(0).get("testclass"));
        assertEquals("$.token", elements.get(0).get("field"));
        assertEquals("token", elements.get(0).get("ref_name"));
        assertEquals("http_header", elements.get(1).get("testclass"));
        assertEquals("regex", elements.get(2).get("testclass"));
        assertEquals("result", elements.get(3).get("testclass"));
        assertThrows(IllegalArgumentException.class,
                () -> SceneRyzeConverter.convertExtractors(List.of(Map.of(
                        "source", "groovy", "variableName", "x"))));
    }

    // ========== 变量引用透传 ==========

    @Test
    void variableReferencesPassThroughToSampler() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", "/api/${host}/port/${port}");
        SceneRyzeConverter.StepSpec step = new SceneRyzeConverter.StepSpec("t", config, List.of(), List.of());

        Map<String, Object> sampler = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(step)));
        String baseUrl = ((Map<?, ?>) sampler.get("config")).get("base_url").toString();
        // 变量引用原样传递给引擎，由 Ryze context chain 运行时求值
        assertTrue(baseUrl.contains("${host}"), baseUrl);
        assertTrue(baseUrl.contains("${port}"), baseUrl);
    }

    @Test
    void anchoredExpectedUsesQuoteForLiteralSafety() {
        String expected = "^a.b$";
        String anchored = SceneRyzeConverter.anchoredExpected("starts_with", expected);
        assertTrue(anchored.startsWith("^"));
        assertTrue(Pattern.compile(anchored).matcher("^a.b$suffix").find()
                == Pattern.compile("^" + Pattern.quote(expected)).matcher("^a.b$suffix").find());
    }
}
