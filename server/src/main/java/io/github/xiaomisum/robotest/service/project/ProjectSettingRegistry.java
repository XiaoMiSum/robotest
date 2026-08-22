package io.github.xiaomisum.robotest.service.project;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 项目设置键注册表（代码常量，非数据库表）。
 *
 * <p>
 * 「域 + 键」白名单与默认值的唯一权威来源：未落库的键视为未配置，读取时返回此处默认值；
 * 新业务域接入时仅在此追加 (domain, key) 定义与校验规则，不改表结构与接口契约。
 * 默认值只在代码中定义、不写入数据库（《项目设置详细设计说明书》2.1.2）。
 * </p>
 */
public final class ProjectSettingRegistry {

    /** 业务域归属（域值预留：V1.2 无 common 落库项，func_test 由功能测试后续迭代注册） */
    public interface Domain {
        String COMMON = "common";
        String API_TEST = "api_test";
        String FUNC_TEST = "func_test";
    }

    public interface Key {
        /** 允许分享接口测试报告 */
        String REPORT_SHARE_ENABLED = "report.share.enabled";
        /** 分享链接有效期（天），生成链接时计算过期时间 */
        String REPORT_SHARE_EXPIRE_DAYS = "report.share.expire-days";
    }

    /**
     * @param validator 设置值合法性校验，语义由键定义
     */
    public record SettingDefinition(String domain, String key, String defaultValue, Predicate<String> validator) {
    }

    private static final Predicate<String> BOOLEAN_VALUE = v -> "true".equals(v) || "false".equals(v);
    private static final Predicate<String> SHARE_EXPIRE_DAYS_VALUE = v -> Set.of("1", "7", "30").contains(v);

    private static final Map<String, SettingDefinition> REGISTRY = Stream.of(
                    new SettingDefinition(Domain.API_TEST, Key.REPORT_SHARE_ENABLED, "false", BOOLEAN_VALUE),
                    new SettingDefinition(Domain.API_TEST, Key.REPORT_SHARE_EXPIRE_DAYS, "7", SHARE_EXPIRE_DAYS_VALUE))
            .collect(Collectors.toUnmodifiableMap(d -> registryKey(d.domain(), d.key()), Function.identity()));

    private ProjectSettingRegistry() {
    }

    public static SettingDefinition find(String domain, String settingKey) {
        return REGISTRY.get(registryKey(domain, settingKey));
    }

    public static List<SettingDefinition> listByDomain(String domain) {
        return REGISTRY.values().stream()
                .filter(d -> d.domain().equals(domain))
                .toList();
    }

    /** 可查询/可更新的域白名单（func_test 尚无落库项定义，暂不开放查询） */
    public static boolean isSupportedDomain(String domain) {
        return Domain.COMMON.equals(domain) || Domain.API_TEST.equals(domain);
    }

    private static String registryKey(String domain, String settingKey) {
        return domain + "|" + settingKey;
    }
}
