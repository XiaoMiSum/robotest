package io.github.xiaomisum.robotest.service.apitest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 平台场景模型 → Ryze TestSuite JSON 转换（测试场景详细设计 4.2/4.3/4.4、基础设施详细设计 4.1.2）。
 * <p>
 * Ryze 6.1.0 实际能力与设计文档转换表存在出入（文档按理想能力编写），本类以实测 KW 注册名为准：
 * 断言元件 status/json/result/http，匹配器 equals/not_equals/contains/not_contains/regex/gt/lt/gte/lte，
 * 提取元件 json/regex/result/http_header；xpath/groovy/boundary 无对应元件，转换期拒绝执行（7003）。
 * starts_with/ends_with 借道 regex 锚点实现，避免引擎侧缺失匹配器。
 * <p>
 * 变量引用使用 Ryze 原生 {@code ${变量名}} 语法，不使用 {@code env:/var:} 前缀。
 * 环境变量与场景变量放在 suite 级 variables，步骤变量放在 sampler 级 variables，
 * 由 Ryze context chain 自动处理继承与覆盖。
 */
public final class SceneRyzeConverter {

    private SceneRyzeConverter() {
    }

    /** 单个步骤的执行规格：请求配置 + 处理器/验证器/提取器（均为平台存储结构） */
    public record StepSpec(String title, Map<String, Object> requestConfig,
            List<Map<String, Object>> validators,
            List<Map<String, Object>> extractors) {
    }

    /**
     * 构建 suite 级变量：环境变量 + 场景变量（场景覆盖同名环境变量）。
     * 变量值保持原始 {@code ${...}} 引用不预解析，由 Ryze 引擎运行时求值。
     */
    public static Map<String, Object> buildSuiteVariables(
            DebugRyzeConverter.EnvSnapshot env,
            List<Map<String, Object>> sceneVariables) {
        Map<String, Object> variables = new LinkedHashMap<>();
        // 环境变量（最低优先级）
        env.variables().forEach((k, v) -> variables.put(k, v == null ? "" : v));
        // 场景变量覆盖环境变量
        collectSceneVariables(variables, sceneVariables);
        return variables;
    }

    /**
     * 构建仅场景变量的 suite 级变量（定时任务多场景组合执行：环境变量由调用方在顶层大 suite 挂载，
     * 子 suite 经由 Ryze context chain 继承，不再并入，见定时任务详细设计 4.3）。
     */
    public static Map<String, Object> buildSceneVariables(List<Map<String, Object>> sceneVariables) {
        Map<String, Object> variables = new LinkedHashMap<>();
        collectSceneVariables(variables, sceneVariables);
        return variables;
    }

    private static void collectSceneVariables(Map<String, Object> variables,
            List<Map<String, Object>> sceneVariables) {
        if (sceneVariables == null) {
            return;
        }
        for (Map<String, Object> var : sceneVariables) {
            Object name = var.get("name");
            if (name != null && !name.toString().isBlank()) {
                variables.put(name.toString(), var.get("value"));
            }
        }
    }

    /**
     * 组装 TestSuite（单套件架构：所有步骤作为 children，extractor 结果自动流向下序步骤）。
     * suite.variables = 环境 + 场景，suite.configelements = 环境 http 配置 + 数据源，
     * sampler.variables = 步骤级（Ryze context chain 自动继承覆盖）。
     */
    public static Map<String, Object> buildSuite(String title,
            DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables,
            List<StepSpec> steps) {
        return buildSuite(title, env, suiteVariables, perStepVariables, steps, List.of());
    }

    /**
     * 组装 TestSuite（单套件架构：所有步骤作为 children，extractor 结果自动流向下序步骤）。
     * suite.variables = 环境 + 场景，suite.configelements = 环境 http 配置 + 数据源，
     * sampler.variables = 步骤级（Ryze context chain 自动继承覆盖）。
     *
     * @param sceneProcessors 场景级处理器（元素 {type: pre|post, name, enabled, testclass, config, extractors}），
     *                        按需求 3.9 与场景处理器合并：前置 环境级→场景级，后置 场景级→环境级。
     */
    public static Map<String, Object> buildSuite(String title,
            DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables,
            List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors) {
        return buildSuiteElement(title, env, suiteVariables, perStepVariables, steps, sceneProcessors,
                null, null, false);
    }

    /**
     * 组装场景子 TestSuite（基础设施详细设计 4.1.2 定时任务多场景组合执行）。
     * 一个场景 = 一个子 TestSuite：variables = 场景自身变量（环境变量由调用方在顶层大 suite 挂载），
     * children = 该场景步骤（sampler），pre/postprocessors = 该场景处理器（环境处理器在顶层挂载，
     * 子 suite 不再并入），configelements 交由顶层 TestSuite 统一挂载。
     * 子 suite 携带 {@code metadata: {sceneId, taskId}}，执行引擎将元数据复制到对应 TestSuiteResult，
     * 调度器据此从单一大 suite 结果树按场景反查结果（定时任务详细设计 4.3）。
     *
     * @param sceneId 场景 ID（写入子 suite metadata，结果反查依据）
     * @param taskId  任务 ID（写入子 suite metadata）
     */
    public static Map<String, Object> buildSceneSuite(String title,
            DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables,
            List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors,
            UUID sceneId, UUID taskId) {
        return buildSuiteElement(title, env, suiteVariables, perStepVariables, steps, sceneProcessors,
                sceneId, taskId, true);
    }

    /**
     * <p>单套件架构：所有步骤作为同一个 TestSuite 的 children，extractor 结果自动流向下序步骤。
     * suite.variables = 环境 + 场景，suite.configelements = 环境 http 配置 + 数据源，
     * sampler.variables = 步骤级（Ryze context chain 自动继承覆盖）。
     *
     * <p>当作为场景子 suite（{@code sceneSuite=true}）时：configelements 由顶层 TestSuite 挂载，
     * 本元素不再重复携带（否则环境 http ref 无法被子级引用），改用 {@code testclass: __testsuite__}
     * 供引擎反序列化为 TestSuite；metadata 写入子 suite 用于结果反查。
     */
    private static Map<String, Object> buildSuiteElement(String title,
            DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables,
            List<StepSpec> steps,
            List<Map<String, Object>> sceneProcessors,
            UUID sceneId, UUID taskId, boolean sceneSuite) {
        Map<String, Object> suite = new LinkedHashMap<>();
        if (sceneSuite) {
            suite.put("testclass", "__testsuite__");
            // 场景子 suite id = sceneId，供结果树/快照直接定位场景；metadata.sceneId 仍用于结果反查
            suite.put("id", sceneId == null ? null : sceneId.toString());
        }
        suite.put("title", title);
        if (suiteVariables != null && !suiteVariables.isEmpty()) {
            suite.put("variables", suiteVariables);
        }
        if (!sceneSuite) {
            List<Map<String, Object>> configElements = buildConfigureElements(env);
            if (!configElements.isEmpty()) {
                suite.put("configelements", configElements);
            }
        }
        if (sceneSuite) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sceneId", sceneId == null ? null : sceneId.toString());
            metadata.put("taskId", taskId == null ? null : taskId.toString());
            suite.put("metadata", metadata);
        }
        Map<String, List<Map<String, Object>>> sceneProcs = splitSceneProcessors(
                sceneProcessors, defaultHttpRef(env));
        List<Map<String, Object>> pre = new ArrayList<>();
        List<Map<String, Object>> post = new ArrayList<>();
        if (sceneSuite) {
            // 场景子 suite 不再合并环境处理器：环境内容（变量/前后置/配置元件）统一挂顶层大 suite，
            // 场景子 suite 仅承载场景自身处理器（基础设施详细设计 4.1.2 多场景组合执行）
            pre.addAll(sceneProcs.get("pre"));
            post.addAll(sceneProcs.get("post"));
        } else {
            // 单套件（单场景/调试）仍将环境处理器并入本 suite：前置 环境级→场景级，后置 场景级→环境级
            pre.addAll(env.preprocessors());
            pre.addAll(sceneProcs.get("pre"));
            post.addAll(sceneProcs.get("post"));
            post.addAll(env.postprocessors());
        }
        if (!pre.isEmpty()) {
            suite.put("preprocessors", pre);
        }
        if (!post.isEmpty()) {
            suite.put("postprocessors", post);
        }
        List<Map<String, Object>> children = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> stepVars = perStepVariables != null && i < perStepVariables.size()
                    ? perStepVariables.get(i) : Map.of();
            children.add(buildSampler(env, stepVars, steps.get(i)));
        }
        suite.put("children", children);
        return suite;
    }

    /**
     * 按 type 拆分场景级处理器为 pre/post 两组，剥离平台 overlay 字段（type/name/enabled），
     * 仅保留 Ryze 元件自身字段（testclass/config/extractors 等），disabled 项直接丢弃。
     * HTTP 处理器 config 缺省 ref 时注入环境默认 http 配置引用（同步骤取样器行为），
     * 否则引擎会以空主机名拼出无效地址（如 http://null/...）。
     */
    private static Map<String, List<Map<String, Object>>> splitSceneProcessors(
            List<Map<String, Object>> sceneProcessors, String defaultHttpRef) {
        List<Map<String, Object>> pre = new ArrayList<>();
        List<Map<String, Object>> post = new ArrayList<>();
        for (Map<String, Object> row : nullSafe(sceneProcessors)) {
            if (Boolean.FALSE.equals(row.get("enabled"))) {
                continue;
            }
            String type = str(row.get("type"));
            if (type.isBlank()) {
                continue;
            }
            Map<String, Object> element = new LinkedHashMap<>(row);
            element.remove("type");
            element.remove("name");
            element.remove("enabled");
            // 处理器提取器为平台存储结构，须转 Ryze 元件格式 {testclass,field,ref_name}，否则引擎无法识别该组件
            if (element.get("extractors") instanceof List<?> rawList) {
                List<Map<String, Object>> extractors = convertExtractors(toStringKeyMapList(rawList));
                if (extractors.isEmpty()) {
                    element.remove("extractors");
                } else {
                    element.put("extractors", extractors);
                }
            }
            if ("pre".equalsIgnoreCase(type) || "preprocessor".equals(type)) {
                injectDefaultRef(element, defaultHttpRef);
                pre.add(element);
            } else if ("post".equalsIgnoreCase(type) || "postprocessor".equals(type)) {
                injectDefaultRef(element, defaultHttpRef);
                post.add(element);
            }
        }
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("pre", pre);
        result.put("post", post);
        return result;
    }

    /** HTTP 处理器未显式指定 ref（且无 base_url/绝对地址）时注入环境默认 ref，与步骤取样器行为一致 */
    @SuppressWarnings("unchecked")
    private static void injectDefaultRef(Map<String, Object> element, String defaultHttpRef) {
        if (defaultHttpRef.isBlank() || !"http".equals(element.get("testclass"))
                || !(element.get("config") instanceof Map<?, ?> rawConfig)) {
            return;
        }
        if (!str(rawConfig.get("ref")).isBlank()) {
            return;
        }
        Object baseUrl = rawConfig.get("base_url");
        if (baseUrl != null && !baseUrl.toString().isBlank()) {
            return;
        }
        Object url = rawConfig.get("url");
        if (url != null && url.toString().matches("^https?://.*")) {
            return;
        }
        // config 可能为不可变 map（如 Map.of），须复制后再注入
        Map<String, Object> config = new LinkedHashMap<>((Map<String, Object>) rawConfig);
        config.put("ref", defaultHttpRef);
        element.put("config", config);
    }

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> list) {
        return list == null ? List.of() : list;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toStringKeyMapList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    /**
     * 引擎反序列化契约（ConfigureElementObjectReader）：{testclass, ref_name, config}。
     * 返回环境 http 配置与数据源的 configelements 列表；http 配置 headers 由 KV-数组行转 map。
     */
    public static List<Map<String, Object>> buildConfigureElements(DebugRyzeConverter.EnvSnapshot env) {
        List<Map<String, Object>> elements = new ArrayList<>();
        for (Map<String, Object> http : env.httpConfigs()) {
            Map<String, Object> config = new LinkedHashMap<>();
            setIfPresent(config, "base_url", http.get("baseUrl"));
            Map<String, Object> headers = toHeaderMap(http.get("headers"));
            if (!headers.isEmpty()) {
                config.put("headers", headers);
            }
            elements.add(element(http.get("refName"), "http", config));
        }
        for (Map<String, Object> ds : env.dataSources()) {
            Map<String, Object> config = new LinkedHashMap<>();
            setIfPresent(config, "driver", ds.get("driver"));
            setIfPresent(config, "url", ds.get("url"));
            // 用户名/密码（v6.0.12 起并入 url，兼容保留独立字段）
            setIfPresent(config, "username", ds.get("username"));
            setIfPresent(config, "password", ds.get("password"));
            Object maxPoolSize = ds.get("maxPoolSize");
            if (maxPoolSize instanceof Number number) {
                config.put("max_active", number.intValue());
            }
            elements.add(element(ds.get("refName"), "jdbc", config));
        }
        return elements;
    }

    /**
     * 默认 http 配置引用名：isDefault=true 优先，否则取首条；无 http 配置返回空串
     * （此时步骤不设 ref，命中引擎默认键 __http_configure_element_default_ref_name__）。
     */
    public static String defaultHttpRef(DebugRyzeConverter.EnvSnapshot env) {
        Map<String, Object> fallback = null;
        for (Map<String, Object> http : env.httpConfigs()) {
            if (Boolean.TRUE.equals(http.get("isDefault"))) {
                return str(http.get("refName"));
            }
            if (fallback == null) {
                fallback = http;
            }
        }
        return fallback == null ? "" : str(fallback.get("refName"));
    }

    private static Map<String, Object> element(Object refName, String testclass, Map<String, Object> config) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("testclass", testclass);
        String ref = str(refName);
        if (!ref.isBlank()) {
            element.put("ref_name", ref);
        }
        if (!config.isEmpty()) {
            element.put("config", config);
        }
        return element;
    }

    private static void setIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value);
        }
    }

    /** http 配置请求头：由 KV-数组行（{key,value,enabled}）或 map 转 map，仅取启用项 */
    private static Map<String, Object> toHeaderMap(Object headers) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (headers instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> map.put(k.toString(), v == null ? "" : v));
        } else if (headers instanceof List<?> rawList) {
            for (Object entryObj : rawList) {
                if (!(entryObj instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object key = entry.get("key");
                if (key == null || Boolean.FALSE.equals(entry.get("enabled"))) {
                    continue;
                }
                Object value = entry.get("value");
                map.put(key.toString(), value == null ? "" : value);
            }
        }
        return map;
    }

    private static Map<String, Object> buildSampler(DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> stepVariables, StepSpec step) {
        Map<String, Object> config = step.requestConfig() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(step.requestConfig());
        // 平台步骤 requestConfig 可能携带 refName（平台字段），Ryze 引擎仅识别 ref
        config.remove("refName");
        config.putIfAbsent("method", "GET");
        String url = str(config.remove("url"));
        String baseUrl = str(config.remove("base_url"));
        // http 配置不再合并进取样器：环境 base_url/headers 由 suite configelements 以 ref 引用
        if (!baseUrl.isBlank()) {
            config.put("base_url", baseUrl);
        } else if (url.matches("^https?://.*")) {
            config.put("base_url", url);
        } else if (!url.isBlank()) {
            config.put("path", url);
        }
        String ref = defaultHttpRef(env);
        if (!ref.isBlank()) {
            config.put("ref", ref);
        }

        Map<String, Object> headers = new LinkedHashMap<>();
        mergeEntries(headers, config.remove("headers"));
        if (!headers.isEmpty()) {
            config.put("headers", headers);
        }
        Map<String, Object> query = new LinkedHashMap<>();
        mergeEntries(query, config.remove("params"));
        if (!query.isEmpty()) {
            config.put("query", query);
        }
        normalizeBody(config);

        Map<String, Object> sampler = new LinkedHashMap<>();
        sampler.put("title", step.title());
        sampler.put("testclass", "http");
        sampler.put("config", config);
        // 步骤级变量放在 sampler 级，Ryze context chain 自动覆盖 suite 级同名变量
        if (stepVariables != null && !stepVariables.isEmpty()) {
            sampler.put("variables", stepVariables);
        }
        List<Map<String, Object>> validators = convertValidators(step.validators());
        if (!validators.isEmpty()) {
            sampler.put("validators", validators);
        }
        List<Map<String, Object>> extractors = convertExtractors(step.extractors());
        if (!extractors.isEmpty()) {
            sampler.put("extractors", extractors);
        }
        return sampler;
    }

    private static void mergeEntries(Map<String, Object> target, Object entriesObj) {
        if (!(entriesObj instanceof List<?> entries)) {
            return;
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> item)) {
                continue;
            }
            Object key = item.get("key");
            if (key == null || Boolean.FALSE.equals(item.get("enabled"))) {
                continue;
            }
            // 通配 Map 的 getOrDefault 受捕获类型限制，改用显式判空
            Object value = item.get("value");
            target.put(key.toString(), value == null ? "" : str(value));
        }
    }

    private static void normalizeBody(Map<String, Object> config) {
        Object body = config.get("body");
        if (!(body instanceof Map<?, ?> bodyMap)) {
            return;
        }
        Object typeObj = bodyMap.get("type");
        String type = str(typeObj == null ? "json" : typeObj).toLowerCase();
        Object content = bodyMap.get("content");
        switch (type) {
            case "none", "" -> config.remove("body");
            // 表单负载以 data(map) 传输，原 body 结构必须移除，避免引擎重复解析
            case "form" -> {
                config.remove("body");
                config.put("data", toFormData(content));
            }
            default -> config.put("body", content);
        }
    }

    /** 表单负载统一为 {key: value} map：场景编辑器发出 KV-数组行，转为 map 供 Ryze http data 使用 */
    private static Map<String, Object> toFormData(Object content) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (content instanceof Map<?, ?> map) {
            map.forEach((k, v) -> data.put(k.toString(), v == null ? "" : str(v)));
        } else if (content instanceof List<?>) {
            mergeEntries(data, content);
        }
        return data;
    }

    // ========== 验证器（测试场景详细设计 4.2 转换表） ==========

    static List<Map<String, Object>> convertValidators(List<Map<String, Object>> validators) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (validators == null) {
            return result;
        }
        for (Map<String, Object> validator : validators) {
            if (Boolean.FALSE.equals(validator.get("enabled"))) {
                continue;
            }
            String target = str(validator.get("target"));
            String condition = str(validator.getOrDefault("condition", "equals"));
            String expected = str(validator.get("expected"));
            String expression = str(validator.get("expression"));
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("title", orDefault(validator.get("name"), target));
            switch (target) {
                case "status_code" -> {
                    element.put("testclass", "http");
                    element.put("field", "status");
                    element.put("expected", expected);
                    element.put("rule", compareRule(condition));
                }
                case "json_field" -> {
                    element.put("testclass", "json");
                    element.put("field", expression);
                    element.put("expected", expected);
                    element.put("rule", compareRule(condition));
                }
                case "response_header" -> {
                    element.put("testclass", "http");
                    // HTTPResponseAssertion 仅识别 header[<i>].<name> 形态，headers.<name> 会落入响应体比较分支
                    element.put("field", "header." + expression);
                    element.put("expected", expected);
                    element.put("rule", compareRule(condition));
                }
                case "response_body" -> {
                    element.put("testclass", "result");
                    // starts_with/ends_with 转锚定正则：引擎匹配器集合无此前缀类断言
                    boolean anchored = "starts_with".equals(condition) || "ends_with".equals(condition);
                    element.put("expected", anchored ? anchoredExpected(condition, expected) : expected);
                    element.put("rule", anchored ? "regex" : compareRule(condition));
                }
                case "regex" -> {
                    // 正则目标语义为整段响应体匹配，走 result 元件而非文档表中的 json 字段提取
                    element.put("testclass", "result");
                    element.put("expected", expression);
                    element.put("rule", "regex");
                }
                default -> throw new IllegalArgumentException(
                        "不支持的验证目标：" + target + "（当前引擎支持 返回码/JSON 字段/响应头/响应体/正则）");
            }
            result.add(element);
        }
        return result;
    }

    static String anchoredExpected(String condition, String expected) {
        return "ends_with".equals(condition)
                ? Pattern.quote(expected) + "$"
                : "^" + Pattern.quote(expected);
    }

    /** 平台比较条件 → Ryze 匹配器规则名（KW 实测注册名） */
    static String compareRule(String condition) {
        return switch (condition) {
            case "equals" -> "equals";
            case "not_equals" -> "not_equals";
            case "contains" -> "contains";
            case "not_contains" -> "not_contains";
            case "greater_than" -> "gt";
            case "less_than" -> "lt";
            case "greater_or_equal" -> "gte";
            case "less_or_equal" -> "lte";
            case "matches_regex" -> "regex";
            default -> throw new IllegalArgumentException("不支持的比较条件：" + condition);
        };
    }

    // ========== 提取器（测试场景详细设计 4.3 转换表） ==========

    static List<Map<String, Object>> convertExtractors(List<Map<String, Object>> extractors) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (extractors == null) {
            return result;
        }
        for (Map<String, Object> extractor : extractors) {
            if (Boolean.FALSE.equals(extractor.get("enabled"))) {
                continue;
            }
            String source = str(extractor.get("source"));
            String expression = str(extractor.get("expression"));
            String variableName = str(extractor.get("variableName"));
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("title", orDefault(extractor.get("name"), variableName));
            switch (source) {
                case "json_field" -> {
                    element.put("testclass", "json");
                    element.put("field", expression);
                }
            case "response_header" -> {
                // 引擎 HTTPHeaderExtractor 注册名为 http（http 与 sampler/processor 存在同名 KW，易混淆）
                element.put("testclass", "http");
                element.put("field", expression);
            }
                case "regex" -> {
                    element.put("testclass", "regex");
                    element.put("field", expression);
                }
                case "full_body" -> element.put("testclass", "result");
                default -> throw new IllegalArgumentException(
                        "不支持的提取来源：" + source + "（当前引擎支持 JSON 字段/响应头/正则/完整响应体）");
            }
            element.put("ref_name", variableName);
            result.add(element);
        }
        return result;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String orDefault(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

}
