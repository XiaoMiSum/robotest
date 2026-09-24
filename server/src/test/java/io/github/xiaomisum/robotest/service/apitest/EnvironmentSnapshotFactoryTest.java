package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiEnvironment;
import io.github.xiaomisum.robotest.repository.apitest.ApiEnvironmentMapper;
import io.github.xiaomisum.robotest.service.apitest.execution.adapters.ryze.RyzeEnvironmentSnapshotProvider;
import io.github.xiaomisum.robotest.service.apitest.execution.ports.EnvSnapshot;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 环境快照处理器标准化：平台存储格式 → Ryze 引擎格式（覆盖字段剥离、提取器转换）。
 */
class EnvironmentSnapshotFactoryTest {

    @Test
    void normalizeProcessorElementStripsOverlayFields() {
        Map<String, Object> element = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/pre"),
                "enabled", true,
                "sortOrder", 1);
        Map<String, Object> result = RyzeEnvironmentSnapshotProvider.normalizeProcessorElement(element);
        assertFalse(result.containsKey("enabled"));
        assertFalse(result.containsKey("sortOrder"));
        assertEquals("http", result.get("testclass"));
        assertEquals(Map.of("path", "/pre"), result.get("config"));
    }

    @Test
    void normalizeProcessorElementConvertsPlatformExtractorsToRyzeFormat() {
        Map<String, Object> element = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/pre"),
                "extractors", List.of(
                        Map.of("source", "json_field", "expression", "$.code",
                                "variableName", "_var1", "enabled", true, "description", "")));
        Map<String, Object> result = RyzeEnvironmentSnapshotProvider.normalizeProcessorElement(element);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> extractors =
                (List<Map<String, Object>>) result.get("extractors");
        assertEquals(1, extractors.size());
        assertEquals("json", extractors.get(0).get("testclass"));
        assertEquals("$.code", extractors.get(0).get("field"));
        assertEquals("_var1", extractors.get(0).get("ref_name"));
        assertFalse(extractors.get(0).containsKey("source"));
        assertFalse(extractors.get(0).containsKey("variableName"));
        assertFalse(extractors.get(0).containsKey("enabled"));
    }

    @Test
    void normalizeProcessorElementRemovesEmptyExtractors() {
        Map<String, Object> element = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/pre"),
                "extractors", List.of(
                        Map.of("source", "", "expression", "", "variableName", "", "enabled", true)));
        Map<String, Object> result = RyzeEnvironmentSnapshotProvider.normalizeProcessorElement(element);
        assertFalse(result.containsKey("extractors"));
    }

    @Test
    void normalizeProcessorElementRetainsRyzeKeys() {
        Map<String, Object> element = Map.of(
                "testclass", "http",
                "config", Map.of("path", "/pre"));
        Map<String, Object> result = RyzeEnvironmentSnapshotProvider.normalizeProcessorElement(element);
        assertEquals("http", result.get("testclass"));
        assertEquals(Map.of("path", "/pre"), result.get("config"));
    }

    @Test
    void resolveWithoutEnvironmentUsesMapperDefaultLookup() {
        ApiEnvironmentMapper mapper = mock(ApiEnvironmentMapper.class);
        UUID projectId = UUID.randomUUID();
        ApiEnvironment environment = new ApiEnvironment();
        environment.setProjectId(projectId);
        environment.setName("默认环境");
        environment.setVariables(List.of(Map.of("name", "BASE_URL", "value", "https://example.com")));
        environment.setProcessors(List.of());
        environment.setHttpConfigs(List.of(Map.of("baseUrl", "https://example.com")));
        environment.setDataSources(List.of());
        when(mapper.findDefaultByProjectId(projectId)).thenReturn(environment);

        RyzeEnvironmentSnapshotProvider provider = new RyzeEnvironmentSnapshotProvider();
        ReflectionTestUtils.setField(provider, "environmentMapper", mapper);

        EnvSnapshot snapshot = provider.resolve(projectId, null);

        assertEquals("默认环境", snapshot.name());
        assertEquals("https://example.com", snapshot.variables().get("BASE_URL"));
        verify(mapper).findDefaultByProjectId(projectId);
    }
}
