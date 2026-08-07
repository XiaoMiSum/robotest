package io.github.xiaomisum.robotest.service.ai.gateway;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiSettingsSchemaRespDTO;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 系统配置项定义注册表（代码内置元数据，详细设计 2.2 / 3.3.8）。
 *
 * <p>单一事实来源：同时驱动 settings-schema 下发、缺省默认值回退与保存时逐项校验。
 * 键清单随功能演进在此增补，前端不硬编码。</p>
 */
@Component
public class AiSettingDefinitions {

    /** planOrder.weights 三权重之和校验容差 */
    private static final double WEIGHT_SUM_TOLERANCE = 0.001;
    /** 全局助手可选写工具枚举（string[] 多选项） */
    private static final List<String> WRITE_TOOL_OPTIONS = List.of("create_bug", "create_plan_draft");

    /** 控件类型 */
    static final String TYPE_INT = "int";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_OBJECT = "object";
    static final String TYPE_STRING_ARRAY = "string[]";

    private final List<Def> definitions = new ArrayList<>();
    private final Map<String, Def> byKey = new LinkedHashMap<>();
    private final Map<String, Object> defaults = new LinkedHashMap<>();

    public AiSettingDefinitions() {
        // 限流阈值（次/小时·人，≥ 1）
        addInt("rateLimit", "限流阈值", "rateLimit.generation", "生成类调用上限", "生成类每用户每小时调用上限", 20, 1.0, null);
        addInt("rateLimit", "限流阈值", "rateLimit.suggestion", "建议类调用上限", "建议类每用户每小时调用上限", 60, 1.0, null);
        addInt("rateLimit", "限流阈值", "rateLimit.retrieval", "检索类调用上限", "检索类每用户每小时调用上限", 120, 1.0, null);
        addInt("rateLimit", "限流阈值", "rateLimit.task", "任务类调用上限", "异步任务类每用户每小时调用上限", 10, 1.0, null);
        addInt("rateLimit", "限流阈值", "rateLimit.assistant", "全局助手调用上限", "全局助手每用户每小时调用上限", 60, 1.0, null);
        // 语义查重
        addInt("dedup", "语义查重", "dedup.topK", "查重返回条数", "语义查重返回的最相似候选条数", 5, 1.0, 50.0);
        addNumber("dedup", "语义查重", "dedup.similarityThreshold", "查重相似度阈值", "判定疑似重复的余弦相似度阈值", 0.75, 0.0, 1.0, 0.01);
        // 聚类分析
        addNumber("clustering", "聚类分析", "clustering.similarityThreshold", "聚类相似度阈值", "缺陷聚类合并的相似度阈值", 0.82, 0.0, 1.0, 0.01);
        addInt("clustering", "聚类分析", "clustering.maxLabeledClusters", "最大标注簇数", "生成标签的最大聚类簇数", 30, 1.0, 100.0);
        // 检索与推荐
        addInt("retrieval", "检索与推荐", "missingPoint.topK", "遗漏点检索条数", "遗漏测试点分析检索的候选条数", 100, 1.0, null);
        addInt("retrieval", "检索与推荐", "regression.topK", "回归推荐条数", "回归子集推荐检索的候选条数", 50, 1.0, null);
        addNumber("retrieval", "检索与推荐", "regression.similarityThreshold", "回归相似度阈值", "回归子集推荐的相似度阈值", 0.7, 0.0, 1.0, 0.01);
        // 执行顺序推荐（权重之和 = 1）
        addWeights("planOrder", "执行顺序推荐", "planOrder.weights", "推荐权重", "执行顺序推荐三项权重（之和须为 1）",
                new LinkedHashMap<>(Map.of("w1", 0.5, "w2", 0.3, "w3", 0.2)));
        // 长度限制
        addInt("length", "长度限制", "importTextMaxLength", "导入文本上限", "外部文本解析导入的最大字符数", 20000, 1000.0, 100000.0);
        addInt("length", "长度限制", "requirementContentMaxLength", "需求内容上限", "需求池条目内容的最大字符数", 20000, 1000.0, 100000.0);
        // 全局助手
        addInt("assistant", "全局助手", "assistantConfirmTimeoutSeconds", "写操作确认超时", "全局助手写操作确认令牌有效期（秒）", 300, 30.0, 3600.0);
        addStringArray("assistant", "全局助手", "assistantWriteToolWhitelist", "写工具白名单", "允许全局助手调用的写操作工具",
                List.of("create_bug", "create_plan_draft"), WRITE_TOOL_OPTIONS);
        // 数据保留
        addInt("retention", "数据保留", "logRetentionDays", "审计日志保留天数", "AI 调用审计日志保留期限（天）", 180, 30.0, 3650.0);
        addInt("retention", "数据保留", "conversationRetentionDays", "会话保留天数", "全局助手会话保留期限（天）", 180, 30.0, 3650.0);
    }

    /**
     * 全量配置项的分组表单定义（供 3.3.8 下发）
     */
    public List<AiSettingsSchemaRespDTO> schema() {
        Map<String, AiSettingsSchemaRespDTO> groups = new LinkedHashMap<>();
        for (Def def : definitions) {
            AiSettingsSchemaRespDTO group = groups.computeIfAbsent(def.group, key -> {
                AiSettingsSchemaRespDTO g = new AiSettingsSchemaRespDTO();
                g.setGroup(def.group);
                g.setGroupLabel(def.groupLabel);
                g.setItems(new ArrayList<>());
                return g;
            });
            AiSettingsSchemaRespDTO.Item item = new AiSettingsSchemaRespDTO.Item();
            item.setKey(def.key);
            item.setType(def.type);
            item.setLabel(def.label);
            item.setDescription(def.description);
            item.setDefaultValue(def.defaultValue);
            item.setMin(def.min);
            item.setMax(def.max);
            item.setStep(def.step);
            item.setOptions(def.options);
            group.getItems().add(item);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * 缺省键的内置默认值（点分键 → 默认值），供合并视图与回退
     */
    public Map<String, Object> defaults() {
        return Map.copyOf(defaults);
    }

    public boolean containsKey(String key) {
        return byKey.containsKey(key);
    }

    /**
     * 逐键校验提交的 settings（未知键、类型不符、越界、权重之和 ≠ 1 均返回 1001）
     */
    public void validate(Map<String, Object> settings) {
        if (settings == null) {
            return;
        }
        settings.forEach((key, value) -> {
            Def def = byKey.get(key);
            if (def == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (value == null) {
                return;
            }
            switch (def.type) {
                case TYPE_INT, TYPE_NUMBER -> validateNumber(def, value);
                case TYPE_OBJECT -> validateWeights(value);
                case TYPE_STRING_ARRAY -> validateStringArray(def, value);
                default -> throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
        });
    }

    /**
     * 仅保留与内置默认值不同的键（保持缺省回退默认值语义，落库仅存覆盖）
     */
    public Map<String, Object> stripDefaults(Map<String, Object> settings) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (settings == null) {
            return overrides;
        }
        settings.forEach((key, value) -> {
            Def def = byKey.get(key);
            if (def != null && value != null && !numericEquals(def, value, def.defaultValue)) {
                overrides.put(key, value);
            }
        });
        return overrides;
    }

    private void validateNumber(Def def, Object value) {
        if (!(value instanceof Number number)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        double d = number.doubleValue();
        if (TYPE_INT.equals(def.type) && d != Math.floor(d)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if ((def.min != null && d < def.min) || (def.max != null && d > def.max)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    private void validateWeights(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        double sum = 0;
        for (String sub : List.of("w1", "w2", "w3")) {
            Object weight = map.get(sub);
            if (!(weight instanceof Number number)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            double d = number.doubleValue();
            if (d < 0 || d > 1) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            sum += d;
        }
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
    }

    private void validateStringArray(Def def, Object value) {
        if (!(value instanceof List<?> list)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        for (Object element : list) {
            if (!(element instanceof String str) || (def.options != null && !def.options.contains(str))) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
        }
    }

    /** 数值键按数值相等判定（避免 20 与 20.0 的类型差异误判为覆盖），其余按 equals */
    private boolean numericEquals(Def def, Object a, Object b) {
        if ((TYPE_INT.equals(def.type) || TYPE_NUMBER.equals(def.type))
                && a instanceof Number na && b instanceof Number nb) {
            return na.doubleValue() == nb.doubleValue();
        }
        return java.util.Objects.equals(a, b);
    }

    private void addInt(String group, String groupLabel, String key, String label, String description,
                        int defaultValue, Double min, Double max) {
        register(new Def(group, groupLabel, key, TYPE_INT, label, description, defaultValue, min, max, null, null));
    }

    private void addNumber(String group, String groupLabel, String key, String label, String description,
                           double defaultValue, Double min, Double max, Double step) {
        register(new Def(group, groupLabel, key, TYPE_NUMBER, label, description, defaultValue, min, max, step, null));
    }

    private void addWeights(String group, String groupLabel, String key, String label, String description,
                            Map<String, Object> defaultValue) {
        register(new Def(group, groupLabel, key, TYPE_OBJECT, label, description, defaultValue, null, null, null, null));
    }

    private void addStringArray(String group, String groupLabel, String key, String label, String description,
                                List<String> defaultValue, List<String> options) {
        register(new Def(group, groupLabel, key, TYPE_STRING_ARRAY, label, description, defaultValue, null, null, null, options));
    }

    private void register(Def def) {
        definitions.add(def);
        byKey.put(def.key, def);
        defaults.put(def.key, def.defaultValue);
    }

    private record Def(String group, String groupLabel, String key, String type, String label, String description,
                       Object defaultValue, Double min, Double max, Double step, List<String> options) {
    }
}
