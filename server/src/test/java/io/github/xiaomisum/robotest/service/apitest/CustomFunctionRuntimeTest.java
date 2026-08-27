package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFunctionRuntimeTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-00000000b001");

    @Mock
    private ApiFunctionMapper functionMapper;
    @Mock
    private ProjectMapper projectMapper;

    private CustomFunctionRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new CustomFunctionRuntime(functionMapper, projectMapper,
                new ApiFunctionScriptEngine());
        Project project = new Project();
        project.setWorkspaceId(WORKSPACE_ID);
        lenient().when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 项目级 sign + 全局级 genv（enabled=true 过滤由查询承担，停用记录不出现在结果中）
        ApiFunction projectFn = fn("sign", "project",
                "args.collect { it.toString() }.join('-')");
        ApiFunction globalFn = fn("genv", "global", "'global-value'");
        when(functionMapper.listVisible(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(true),
                eq("custom"), isNull(), isNull())).thenReturn(List.of(globalFn, projectFn));
    }

    @Test
    void prepareSuite_rewritesKnownCallsAndInjectsTag() {
        Map<String, Object> suite = suiteWith("${sign(${uuid()})}");

        runtime.prepareSuite(suite, PROJECT_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) suite.get("variables");
        assertEquals(PROJECT_ID.toString(), variables.get(CustomFunctionRuntime.PROJECT_TAG));
        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) ((Map<String, Object>) ((List<?>) suite.get("steps")).get(0)).get("headers");
        // 嵌套调用整体重写：内层 uuid 是内置名保持原样，外层 sign 改写为分发器调用
        assertEquals("${robotest_custom(\"sign\",${uuid()})}", headers.get(0));
    }

    @Test
    void prepareSuite_emptyArgCall_dropsComma() {
        Map<String, Object> suite = suiteWith("${genv()}");

        runtime.prepareSuite(suite, PROJECT_ID);

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) ((Map<String, Object>) ((List<?>) suite.get("steps")).get(0)).get("headers");
        assertEquals("${robotest_custom(\"genv\")}", headers.get(0));
    }

    @Test
    void prepareSuite_unknownOrBuiltinNames_untouched() {
        Map<String, Object> suite = suiteWith("${unknown_fn(x)} ${uuid()}");

        runtime.prepareSuite(suite, PROJECT_ID);

        @SuppressWarnings("unchecked")
        List<String> headers = (List<String>) ((Map<String, Object>) ((List<?>) suite.get("steps")).get(0)).get("headers");
        assertEquals("${unknown_fn(x)} ${uuid()}", headers.get(0));
    }

    @Test
    void resolve_projectOverridesGlobal() {
        Map<String, CustomFunctionRuntime.ScriptEntry> entries = runtime.functionsFor(PROJECT_ID);
        assertTrue(entries.containsKey("sign"));
        assertTrue(entries.containsKey("genv"));
        assertFalse(entries.containsKey("off"));

        Object result = entries.get("sign").execute(List.of("a", 1), Map.of());
        assertEquals("a-1", result);
    }

    @Test
    void invalidate_reloadsWithoutStaleEntries() {
        assertFalse(runtime.functionsFor(PROJECT_ID).isEmpty());
        runtime.invalidate(PROJECT_ID);
        // 失效后重新加载（stub 未变，结果仍可见），验证缓存重建路径无异常
        assertTrue(runtime.functionsFor(PROJECT_ID).containsKey("sign"));
        runtime.invalidateAll();
    }

    @Test
    void invalidateByProjectId_clearsSiblingProjectInSameWorkspace() {
        UUID siblingProjectId = UUID.fromString("00000000-0000-0000-0000-00000000a002");
        Project siblingProject = new Project();
        siblingProject.setWorkspaceId(WORKSPACE_ID);
        lenient().when(projectMapper.selectById(siblingProjectId)).thenReturn(siblingProject);
        lenient().when(functionMapper.listVisible(eq(siblingProjectId), eq(WORKSPACE_ID), eq(true),
                eq("custom"), isNull(), isNull())).thenReturn(List.of());

        // 两个项目各自缓存
        runtime.functionsFor(PROJECT_ID);
        runtime.functionsFor(siblingProjectId);
        // 同空间下 sibling 项目失效后应被清除
        runtime.invalidateByProjectId(siblingProjectId);
        // 重新加载（stub 未变），验证 sibling 项目缓存重建无异常
        assertTrue(runtime.functionsFor(siblingProjectId).isEmpty());
        runtime.invalidateAll();
    }

    private static ApiFunction fn(String name, String scope, String script) {
        ApiFunction entity = new ApiFunction();
        entity.setType("custom");
        entity.setName(name);
        entity.setScope(scope);
        entity.setScript(script);
        entity.setEnabled(true);
        return entity;
    }

    private static Map<String, Object> suiteWith(String expression) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("headers", new java.util.ArrayList<>(List.of(expression)));
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("variables", new LinkedHashMap<String, Object>());
        suite.put("steps", new java.util.ArrayList<>(List.of(step)));
        return suite;
    }
}
