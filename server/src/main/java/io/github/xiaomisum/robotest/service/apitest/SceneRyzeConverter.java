package io.github.xiaomisum.robotest.service.apitest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (sceneVariables != null) {
            for (Map<String, Object> var : sceneVariables) {
                Object name = var.get("name");
                if (name != null && !name.toString().isBlank()) {
                    variables.put(name.toString(), var.get("value"));
                }
            }
        }
        return variables;
    }

    /**
     * 组装 TestSuite（单套件架构：所有步骤作为 children，extractor 结果自动流向下序步骤）。
     * suite.variables = 环境 + 场景，sampler.variables = 步骤级（Ryze context chain 自动继承覆盖）。
     */
    public static Map<String, Object> buildSuite(String title,
            DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> suiteVariables,
            List<Map<String, Object>> perStepVariables,
            List<StepSpec> steps) {
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("title", title);
        if (suiteVariables != null && !suiteVariables.isEmpty()) {
            suite.put("variables", suiteVariables);
        }
        // 环境全局前后置处理器直接透传；场景级处理器列表不区分类型，由前端按序维护
        if (!env.preprocessors().isEmpty()) {
            suite.put("preprocessors", env.preprocessors());
        }
        if (!env.postprocessors().isEmpty()) {
            suite.put("postprocessors", env.postprocessors());
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

    private static Map<String, Object> buildSampler(DebugRyzeConverter.EnvSnapshot env,
            Map<String, Object> stepVariables, StepSpec step) {
        Map<String, Object> config = step.requestConfig() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(step.requestConfig());
        config.putIfAbsent("method", "GET");
        String url = str(config.remove("url"));
        String baseUrl = str(config.remove("base_url"));
        // 与调试域一致的保守策略：默认 HTTP 配置拼进步骤 base_url，不依赖引擎多配置解析顺序
        if (baseUrl.isBlank()) {
            baseUrl = env.baseUrl();
        }
        boolean absolute = url.toLowerCase().matches("^https?://.*");
        config.put("base_url", absolute ? url : baseUrl + url);

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.putAll(env.headers());
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
            // 表单负载以 data 传输，原 body 结构必须移除，避免引擎重复解析
            case "form" -> {
                config.remove("body");
                config.put("data", content);
            }
            default -> config.put("body", content);
        }
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
                    element.put("testclass", "status");
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
                    element.put("field", "headers." + expression);
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
                    element.put("testclass", "http_header");
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
