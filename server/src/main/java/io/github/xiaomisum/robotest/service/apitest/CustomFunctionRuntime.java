package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.apitest.ApiFunctionMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.ryze.context.ContextWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义函数运行时：执行期缓存、名称重写与项目上下文注入的统一入口。
 *
 * <p>为什么在执行前重写调用名：自定义函数无法注册进 Ryze 的静态函数注册表，
 * 统一经 {@link RobotestCustomFunctionDispatcher}（robotest）分发；
 * 重写仅针对当前项目可见且启用的自定义函数名，未命中名称保持原样交由框架按内置函数处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFunctionRuntime {

    /** 注入到用例根变量的项目标识，供分发器回查函数归属（C4：不进 URL，随上下文传递） */
    public static final String PROJECT_TAG = "__robotest_project_id";

    private static final Pattern CALL_PATTERN = Pattern.compile("\\$\\{(\\w+)\\(");

    private final ApiFunctionMapper functionMapper;
    private final ProjectMapper projectMapper;
    private final ApiFunctionScriptEngine scriptEngine;

    /** 项目 → 函数名 → 脚本条目；CRUD 后按作用域失效 */
    private final ConcurrentHashMap<UUID, Map<String, ScriptEntry>> caches = new ConcurrentHashMap<>();

    @PostConstruct
    void bindDispatcher() {
        RobotestCustomFunctionDispatcher.bind(this);
    }

    /**
     * 加载当前项目可见（含空间/全局）且启用的自定义函数，解析优先级 项目 &gt; 空间 &gt; 全局。
     *
     * <p>空间归属由项目行反查：执行链路（含异步线程）只稳定持有项目标识。</p>
     */
    public Map<String, ScriptEntry> functionsFor(UUID projectId) {
        return caches.computeIfAbsent(projectId, id -> load(id, resolveWorkspaceId(id)));
    }

    public ScriptEntry resolve(UUID projectId, String name) {
        Map<String, ScriptEntry> entries = caches.get(projectId);
        return entries != null ? entries.get(name) : null;
    }

    public void invalidate(UUID projectId) {
        if (projectId != null) {
            caches.remove(projectId);
        }
    }

    /**
     * 按空间失效：空间作用域函数变更时，该空间下所有项目的缓存均需重建。
     * 由 projectId 反查 workspaceId，再清除同空间内所有已缓存的项目。
     */
    public void invalidateByProjectId(UUID projectId) {
        if (projectId == null) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            caches.remove(projectId);
            return;
        }
        UUID workspaceId = project.getWorkspaceId();
        caches.keySet().removeIf(pid -> {
            Project p = projectMapper.selectById(pid);
            return p != null && workspaceId.equals(p.getWorkspaceId());
        });
    }

    public void invalidateAll() {
        caches.clear();
    }

    /**
     * 执行前处理：注入项目标识标签并递归重写套件树中的自定义函数调用名。
     *
     * <p>直接原地修改传入结构——suite 由转换器新建的 Map 构成，无共享引用。</p>
     */
    public void prepareSuite(Map<String, Object> suite, UUID projectId) {
        Set<String> names = functionsFor(projectId).keySet();
        Object rawVariables = suite.get("variables");
        Map<String, Object> rootVariables;
        if (rawVariables instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            rootVariables = typed;
        } else {
            rootVariables = new java.util.LinkedHashMap<>();
            suite.put("variables", rootVariables);
        }
        rootVariables.putIfAbsent(PROJECT_TAG, projectId.toString());
        if (!names.isEmpty()) {
            walk(suite, names);
        }
    }

    /** 从执行上下文读取项目标识（分发器回调） */
    public UUID resolveProjectId(ContextWrapper contextWrapper) {
        Object value = contextWrapper.getAllVariablesWrapper().get(PROJECT_TAG);
        if (value == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED,
                    "执行上下文缺少项目标识，无法解析自定义函数");
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.API_FUNCTION_EVAL_FAILED,
                    "执行上下文项目标识非法: " + value);
        }
    }

    private UUID resolveWorkspaceId(UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        return project != null ? project.getWorkspaceId() : null;
    }

    private Map<String, ScriptEntry> load(UUID projectId, UUID workspaceId) {
        List<io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction> records =
                functionMapper.listVisible(projectId, workspaceId, true, "custom", null, null);
        List<io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction> ordered = new ArrayList<>(records);
        // 全局最先放入、项目最后覆盖，实现「项目 > 空间 > 全局」就近优先
        ordered.sort(Comparator.comparingInt(entity -> switch (entity.getScope()) {
            case "global" -> 0;
            case "workspace" -> 1;
            default -> 2;
        }));
        Map<String, ScriptEntry> entries = new ConcurrentHashMap<>();
        for (io.github.xiaomisum.robotest.model.entity.apitest.ApiFunction entity : ordered) {
            entries.put(entity.getName(), new ScriptEntry(entity.getName(), entity.getScript()));
        }
        return entries;
    }

    private void walk(Object node, Set<String> names) {
        if (node instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            for (Map.Entry<Object, Object> entry : mutable.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text && text.contains("${")) {
                    entry.setValue(rewriteCalls(text, names));
                } else {
                    walk(value, names);
                }
            }
        } else if (node instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            ListIterator<Object> iterator = ((List<Object>) list).listIterator();
            while (iterator.hasNext()) {
                Object item = iterator.next();
                if (item instanceof String text && text.contains("${")) {
                    iterator.set(rewriteCalls(text, names));
                } else {
                    walk(item, names);
                }
            }
        }
    }

    /**
     * 将已知名调用改写为统一分发器调用：
     * {@code ${name(a)}} → {@code ${robotest("name", a)}}；零参形式去掉多余逗号。
     */
    private static String rewriteCalls(String text, Set<String> names) {
        Matcher matcher = CALL_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        int last = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!names.contains(name)) {
                continue;
            }
            int pos = matcher.end();
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            boolean emptyCall = pos < text.length() && text.charAt(pos) == ')';
            result.append(text, last, matcher.start());
            if (emptyCall) {
                result.append("${robotest_custom(\"").append(name).append("\")");
                last = pos + 1;
            } else {
                result.append("${robotest_custom(\"").append(name).append("\",");
                last = matcher.end();
            }
        }
        result.append(text.substring(last));
        return result.toString();
    }

    /** 编译产物包装：缓存脚本原文引用，求值委托沙箱引擎 */
    public class ScriptEntry {

        private final String name;
        private final String script;

        ScriptEntry(String name, String script) {
            this.name = name;
            this.script = script;
        }

        public String getName() {
            return name;
        }

        public Object execute(List<Object> args, Map<String, Object> contextVariables) {
            return scriptEngine.evaluate(script, args, contextVariables);
        }
    }
}
