package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ryze 套件 → 平台场景转换测试：步骤键与 SceneStepEditor.newStep 对齐，变量行结构与编辑器一致（详设 6.3）。
 */
class RyzeYamlToSceneConverterTest {

    private static Map<String, Object> suite() {
        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("title", "查询订单");
        sampler.put("testclass", "HTTP");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("base_url", "http://example.com");
        config.put("method", "GET");
        config.put("path", "/order/query");
        config.put("headers", Map.of("token", "t1"));
        config.put("query", Map.of("status", "paid"));
        sampler.put("config", config);

        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("title", "查询订单状态");
        suite.put("variables", Map.of("tenant", "2"));
        suite.put("children", List.of(sampler));
        return suite;
    }

    @Test
    void convertSuiteEmitsPlatformSceneSchema() {
        Map<String, Object> scene = RyzeYamlToSceneConverter.convertSuite(suite());

        assertEquals("查询订单状态", scene.get("name"));
        assertNull(scene.get("description"));
        List<?> variables = (List<?>) scene.get("variables");
        assertEquals(1, variables.size());
        Map<?, ?> variable = (Map<?, ?>) variables.get(0);
        assertEquals("tenant", variable.get("name"));
        assertEquals("2", variable.get("value"));
        assertTrue(variable.containsKey("description"));
    }

    @Test
    void convertChildrenEmitsFullStepSchema() {
        Map<String, Object> scene = RyzeYamlToSceneConverter.convertSuite(suite());

        List<?> steps = (List<?>) scene.get("steps");
        assertEquals(1, steps.size());
        Map<?, ?> step = (Map<?, ?>) steps.get(0);
        assertNotNull(step.get("id"));
        assertEquals("查询订单", step.get("name"));
        assertEquals("http", step.get("stepType"));
        assertEquals(true, step.get("enabled"));
        assertEquals("custom", step.get("sourceType"));
        assertNull(step.get("sourceId"));
        assertEquals(1, step.get("sortOrder"));
        assertTrue(step.containsKey("requestConfig"));
        assertEquals(List.of(), step.get("processors"));
        assertEquals(List.of(), step.get("variables"));
        assertTrue(step.containsKey("validators"));
        assertTrue(step.containsKey("extractors"));
        // 键与平台编辑器一致：不再携带 Ryze 侧 type 键
        assertNull(step.get("type"));
        // base_url 归一为 url、headers/query 归一为条目列表
        Map<?, ?> requestConfig = (Map<?, ?>) step.get("requestConfig");
        assertEquals("http://example.com", requestConfig.get("url"));
        assertEquals("GET", requestConfig.get("method"));
        assertTrue(requestConfig.get("headers") instanceof List);
        assertTrue(requestConfig.get("params") instanceof List);
    }

    @Test
    void convertChildrenAssignsSortOrderSequence() {
        Map<String, Object> suite = suite();
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("title", "下一步");
        second.put("testclass", "http");
        suite.put("children", List.of(suite.get("children"), List.of(second)).stream()
                .flatMap(list -> ((List<?>) list).stream()).toList());

        List<?> steps = (List<?>) RyzeYamlToSceneConverter.convertSuite(suite).get("steps");

        assertEquals(2, steps.size());
        assertEquals(1, ((Map<?, ?>) steps.get(0)).get("sortOrder"));
        assertEquals(2, ((Map<?, ?>) steps.get(1)).get("sortOrder"));
    }

    @Test
    void blankTestClassDefaultsToHttp() {
        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("title", "无类型");

        Map<String, Object> step = RyzeYamlToSceneConverter.convertChildren(List.of(sampler)).get(0);

        assertEquals("http", step.get("stepType"));
    }
}
