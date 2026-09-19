package io.github.xiaomisum.robotest.service.apitest;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.StepSpec;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.DebugRyzeConverter;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.SceneRyzeConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static EnvSnapshot env() {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("envVar", "v1");
        variables.put("host", "resolved.example.com");
        List<Map<String, Object>> httpConfigs = List.of(
                Map.of("name", "默认", "refName", "default-http",
                        "baseUrl", "http://env.example.com",
                        "headers", List.of(Map.of("key", "X-Env-Header", "value", "env-value", "enabled", true)),
                        "isDefault", true),
                Map.of("name", "备用", "refName", "alt-http", "baseUrl", "http://alt.example.com", "isDefault", false));
        return new EnvSnapshot(
                "测试环境", variables, List.of(), List.of(), httpConfigs, List.of());
    }

    private static StepSpec spec(String url) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", url);
        return new StepSpec("step-1", config, List.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSampler(Map<String, Object> suite) {
        return (Map<String, Object>) ((List<Object>) suite.get("children")).get(0);
    }

    // ========== 套件组装 ==========

    @Test
    void buildSceneVariablesCarriesOnlySceneVariables() {
        // 场景子 suite 变量仅场景变量，不并入环境变量（定时任务详细设计 4.3）
        Map<String, Object> vars = SceneRyzeConverter.buildSceneVariables(
                List.of(Map.of("name", "sceneVar", "value", "s1")));
        assertEquals(1, vars.size());
        assertEquals("s1", vars.get("sceneVar"));
        assertFalse(vars.containsKey("envVar"));
    }

    @Test
    void buildSceneSuiteCarriesSceneIdOnSuiteAndMetadata() {
        // 场景子 suite 顶层 id = sceneId，metadata 携带 sceneId/taskId（定时任务详细设计 4.3）
        UUID sceneId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Map<String, Object> suite = SceneRyzeConverter.buildSceneSuite("场景A", env(),
                Map.of(), List.of(), List.of(spec("/a")), List.of(), sceneId, taskId);
        assertEquals(sceneId.toString(), suite.get("id"));
        Map<?, ?> metadata = (Map<?, ?>) suite.get("metadata");
        assertEquals(sceneId.toString(), metadata.get("sceneId"));
        assertEquals(taskId.toString(), metadata.get("taskId"));
    }

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
    void relativeUrlGoesToPathAndRefsDefaultHttpConfig() {
        Map<String, Object> sampler = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/api/a"))));
        Map<?, ?> config = (Map<?, ?>) sampler.get("config");
        // http 配置不再合并进取样器：相对路径映射为 path，经 ref 继承环境默认配置
        assertEquals("/api/a", config.get("path"));
        assertEquals("default-http", config.get("ref"));
        assertNull(config.get("base_url"));
    }

    @Test
    void absoluteUrlOverridesBaseUrlAndDisabledHttpConfigKeepsFirstRef() {
        EnvSnapshot env = new EnvSnapshot(
                null, Map.of(), List.of(), List.of(),
                List.of(Map.of("refName", "alt", "baseUrl", "http://alt.example.com", "isDefault", false)),
                List.of());
        Map<String, Object> absolute = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env, Map.of(), List.of(), List.of(spec("http://other.example.com/x"))));
        Map<?, ?> absConfig = (Map<?, ?>) absolute.get("config");
        // 绝对 URL 走步骤级 base_url 覆盖；仍携带默认配置引用以继承环境 headers
        assertEquals("http://other.example.com/x", absConfig.get("base_url"));
        assertEquals("alt", absConfig.get("ref"));

        // 无 http 配置时不设 ref，避免引用不存在的配置
        Map<String, Object> noEnv = firstSampler(SceneRyzeConverter.buildSuite(
                "s", EnvSnapshot.empty(), Map.of(), List.of(),
                List.of(spec("/rel"))));
        assertNull(((Map<?, ?>) noEnv.get("config")).get("ref"));
    }

    @Test
    void stepHeadersNoLongerMergeEnvHeadersOnlyConfigureElementCarriesThem() {
        StepSpec step = new StepSpec("step",
                new java.util.LinkedHashMap<>(Map.of(
                        "method", "GET", "url", "/a",
                        "headers", List.of(
                                Map.of("key", "X-Only-Step", "value", "yes")))),
                List.of(), List.of());
        Map<String, Object> sampler = firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(), List.of(step)));
        Map<?, ?> headers = (Map<?, ?>) ((Map<?, ?>) sampler.get("config")).get("headers");
        // 环境 headers 随 configelements，不再并入步骤
        assertEquals("yes", headers.get("X-Only-Step"));
        assertFalse(headers.containsKey("X-Env-Header"));
    }

    @Test
    void configureElementsCarryHttpAndDatasourceWithMergedHeaders() {
        EnvSnapshot env = new EnvSnapshot(
                null, Map.of(), List.of(), List.of(),
                List.of(Map.of("refName", "a", "baseUrl", "http://a.com",
                        "headers", List.of(Map.of("key", "X-A", "value", "1", "enabled", true)))),
                List.of(Map.of("refName", "db", "driver", "com.mysql.cj.jdbc.Driver",
                        "url", "jdbc:mysql://db", "maxPoolSize", 5)));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env, Map.of(), List.of(), List.of(spec("/a")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) suite.get("configelements");
        assertEquals(2, elements.size());
        Map<String, Object> http = elements.get(0);
        assertEquals("http", http.get("testclass"));
        assertEquals("a", http.get("ref_name"));
        assertEquals("http://a.com", ((Map<?, ?>) http.get("config")).get("base_url"));
        assertEquals("1", ((Map<?, ?>) ((Map<?, ?>) http.get("config")).get("headers")).get("X-A"));
        Map<String, Object> jdbc = elements.get(1);
        assertEquals("jdbc", jdbc.get("testclass"));
        assertEquals("db", jdbc.get("ref_name"));
        Map<?, ?> jdbcConfig = (Map<?, ?>) jdbc.get("config");
        assertEquals("com.mysql.cj.jdbc.Driver", jdbcConfig.get("driver"));
        assertEquals(5, jdbcConfig.get("max_active"));
        assertNull(jdbc.get("ref"));
    }

    @Test
    void sceneProcessorsMergedWithEnvPreAndPost() {
        EnvSnapshot env = new EnvSnapshot(
                null, Map.of(),
                List.of(Map.of("testclass", "http", "config", Map.of("path", "/env-pre"))),
                List.of(Map.of("testclass", "http", "config", Map.of("path", "/env-post"))),
                List.of(), List.of());
        // 场景处理器：扁平 Ryze 元件 + type/name/enabled overlay
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "pre", "name", "场景前置", "enabled", true,
                        "testclass", "http", "config", Map.of("path", "/scene-pre")),
                Map.of("type", "post", "name", "场景后置", "enabled", true,
                        "testclass", "http", "config", Map.of("path", "/scene-post")),
                Map.of("type", "pre", "name", "禁用", "enabled", false,
                        "testclass", "http", "config", Map.of("path", "/skip")));

        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env, Map.of(), List.of(), List.of(spec("/a")), sceneProcessors);

        // 前置：环境级 → 场景级；禁用项剔除，overlay 字段剥离
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        assertEquals(2, pre.size());
        assertEquals("/env-pre", ((Map<?, ?>) pre.get(0).get("config")).get("path"));
        assertEquals("/scene-pre", ((Map<?, ?>) pre.get(1).get("config")).get("path"));
        assertFalse(pre.get(1).containsKey("type"));
        assertFalse(pre.get(1).containsKey("name"));
        assertFalse(pre.get(1).containsKey("enabled"));

        // 后置：场景级 → 环境级
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> post = (List<Map<String, Object>>) suite.get("postprocessors");
        assertEquals(2, post.size());
        assertEquals("/scene-post", ((Map<?, ?>) post.get(0).get("config")).get("path"));
        assertEquals("/env-post", ((Map<?, ?>) post.get(1).get("config")).get("path"));
    }

    @Test
    void noSceneProcessorsKeepsOnlyEnvProcessors() {
        EnvSnapshot env = new EnvSnapshot(
                null, Map.of(),
                List.of(Map.of("testclass", "http", "config", Map.of("path", "/env-pre"))),
                List.of(), List.of(), List.of());
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env, Map.of(), List.of(), List.of(spec("/a")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        assertEquals(1, pre.size());
        assertNull(suite.get("postprocessors"));
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
                        List.of(new StepSpec("t", none, List.of(), List.of())))).get("config");
        assertFalse(noneConfig.containsKey("body"));

        Map<String, Object> form = new java.util.LinkedHashMap<>(base);
        form.put("url", "/f");
        form.put("body", Map.of("type", "form", "content",
                List.of(Map.of("key", "a", "value", "1", "enabled", true),
                        Map.of("key", "skip", "value", "x", "enabled", false))));
        Map<?, ?> formConfig = (Map<?, ?>) firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(),
                        List.of(new StepSpec("t", form, List.of(), List.of())))).get("config");
        assertEquals(Map.of("a", "1"), formConfig.get("data"));
        assertFalse(formConfig.containsKey("body"));
    }

    @Test
    void samplerConfigStripsPlatformRefName() {
        // 平台步骤 requestConfig 可能携带 refName（平台字段），Ryze 引擎仅识别 ref
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", "/a");
        config.put("refName", "default");
        StepSpec step = new StepSpec("s", config, List.of(), List.of());
        Map<String, Object> sampler = firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(), List.of(step)));
        Map<?, ?> samplerConfig = (Map<?, ?>) sampler.get("config");
        assertFalse(samplerConfig.containsKey("refName"),
                "平台字段 refName 不应出现在 Ryze 取样器 config 中");
        assertEquals("default-http", samplerConfig.get("ref"));
    }

    // ========== 验证器转换表 ==========

    @Test
    void statusCodeValidatorMapsToHttpElementWithStatusField() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "status_code", "condition", "equals", "expected", 200, "name", "码")));
        assertEquals(1, elements.size());
        Map<String, Object> element = elements.get(0);
        assertEquals("http", element.get("testclass"));
        assertEquals("status", element.get("field"));
        // expected 经平台层字符串化，引擎侧按数值解析
        assertEquals("200", element.get("expected"));
        assertEquals("equals", element.get("rule"));
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
    void responseHeaderValidatorUsesEngineHeaderFieldShape() {
        List<Map<String, Object>> elements = SceneRyzeConverter.convertValidators(List.of(Map.of(
                "target", "response_header", "expression", "Content-Type",
                "condition", "contains", "expected", "json")));
        assertEquals("http", elements.get(0).get("testclass"));
        assertEquals("header.Content-Type", elements.get(0).get("field"));
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
        assertEquals("http", elements.get(1).get("testclass"));
        assertEquals("Request-Id", elements.get(1).get("field"));
        assertEquals("rid", elements.get(1).get("ref_name"));
        assertEquals("regex", elements.get(2).get("testclass"));
        assertEquals("result", elements.get(3).get("testclass"));
        assertThrows(IllegalArgumentException.class,
                () -> SceneRyzeConverter.convertExtractors(List.of(Map.of(
                        "source", "groovy", "variableName", "x"))));
    }

    // ========== 变量引用透传 ==========

    @Test
    void httpStepRequestMethodIsConverted() {
        StepSpec step = new StepSpec("t",
                new java.util.LinkedHashMap<>(Map.of("method", "POST", "url", "/a")),
                List.of(), List.of());
        Map<String, Object> sampler = firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(), List.of(step)));
        assertEquals("POST", ((Map<?, ?>) sampler.get("config")).get("method"));
    }

    @Test
    void processorRequestMethodIsConverted() {
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "pre", "name", "前置", "enabled", true,
                        "testclass", "http", "config", Map.of("method", "PUT", "path", "/token")));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/a")), sceneProcessors);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        assertEquals("PUT", ((Map<?, ?>) pre.get(0).get("config")).get("method"));
    }

    @Test
    void sceneProcessorExtractorsAreConvertedToEngineComponents() {
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "pre", "name", "前置", "enabled", true,
                        "testclass", "http",
                        "config", Map.of("method", "PUT", "path", "/token"),
                        "extractors", List.of(
                                Map.of("source", "json_field", "expression", "$.code",
                                        "variableName", "_code_1"),
                                Map.of("source", "response_header", "expression", "Set-Cookie",
                                        "variableName", "sid", "enabled", false))));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/a")), sceneProcessors);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extractors = (List<Map<String, Object>>) pre.get(0).get("extractors");
        // 平台提取器转换并被丢弃禁用项
        assertEquals(1, extractors.size());
        assertEquals("json", extractors.get(0).get("testclass"));
        assertEquals("$.code", extractors.get(0).get("field"));
        assertEquals("_code_1", extractors.get(0).get("ref_name"));
        // 平台 overlay 字段被剥离
        assertFalse(((Map<?, ?>) pre.get(0)).containsKey("type"));
        assertFalse(((Map<?, ?>) pre.get(0)).containsKey("name"));
        assertFalse(((Map<?, ?>) pre.get(0)).containsKey("enabled"));
    }

    @Test
    void httpProcessorWithoutRefGetsEnvironmentDefaultRef() {
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "pre", "name", "前置", "enabled", true,
                        "testclass", "http", "config", Map.of("method", "GET", "path", "/token")));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/a")), sceneProcessors);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        assertEquals("default-http", ((Map<?, ?>) pre.get(0).get("config")).get("ref"));
    }

    @Test
    void httpProcessorRefFallbackRespectsExplicitRefAndBaseUrl() {
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "pre", "name", "显式", "enabled", true,
                        "testclass", "http", "config", Map.of("ref", "alt-http", "path", "/a")),
                Map.of("type", "pre", "name", "绝对", "enabled", true,
                        "testclass", "http", "config", Map.of("base_url", "http://x.com", "path", "/b")));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(spec("/a")), sceneProcessors);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pre = (List<Map<String, Object>>) suite.get("preprocessors");
        Map<?, ?> explicitConfig = (Map<?, ?>) pre.get(0).get("config");
        assertEquals("alt-http", explicitConfig.get("ref"));
        Map<?, ?> absConfig = (Map<?, ?>) pre.get(1).get("config");
        assertNull(absConfig.get("ref"));
    }

    @Test
    void httpProcessorNeverGetsRefWithoutEnvHttpConfig() {
        List<Map<String, Object>> sceneProcessors = List.of(
                Map.of("type", "post", "name", "后置", "enabled", true,
                        "testclass", "http", "config", Map.of("path", "/clean")));
        Map<String, Object> suite = SceneRyzeConverter.buildSuite(
                "s", EnvSnapshot.empty(), Map.of(), List.of(),
                List.of(spec("/a")), sceneProcessors);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> post = (List<Map<String, Object>>) suite.get("postprocessors");
        assertNull(((Map<?, ?>) post.get(0).get("config")).get("ref"));
    }

    @Test
    void stepExtractorsAndValidatorsPlacedOnSampler() {
        StepSpec step = new StepSpec("t",
                new java.util.LinkedHashMap<>(Map.of("method", "GET", "url", "/a")),
                List.of(Map.of("target", "status_code", "condition", "equals", "expected", 200)),
                List.of(Map.of("source", "json_field", "expression", "$.token", "variableName", "token")));
        Map<String, Object> sampler = firstSampler(
                SceneRyzeConverter.buildSuite("s", env(), Map.of(), List.of(), List.of(step)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extractors = (List<Map<String, Object>>) sampler.get("extractors");
        assertEquals(1, extractors.size());
        assertEquals("json", extractors.get(0).get("testclass"));
        assertEquals("token", extractors.get(0).get("ref_name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> validators = (List<Map<String, Object>>) sampler.get("validators");
        assertEquals(1, validators.size());
        assertEquals("http", validators.get(0).get("testclass"));
    }

    @Test
    void variableReferencesPassThroughToSampler() {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("method", "GET");
        config.put("url", "/api/${host}/port/${port}");
        StepSpec step = new StepSpec("t", config, List.of(), List.of());

        Map<String, Object> sampler = firstSampler(SceneRyzeConverter.buildSuite(
                "s", env(), Map.of(), List.of(), List.of(step)));
        String path = ((Map<?, ?>) sampler.get("config")).get("path").toString();
        // 变量引用原样传递给引擎，由 Ryze context chain 运行时求值
        assertTrue(path.contains("${host}"), path);
        assertTrue(path.contains("${port}"), path);
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
