package io.github.xiaomisum.robotest.service.ai.recommend;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 优先级关键词规则引擎（详细设计 4.3）：内置于后端的常量规则表，按 P0 → P1 → P3 顺序短路匹配。
 *
 * <p>
 * 规则表不产出 P2（P2 仅来自 LLM），全部未命中返回 null——由调用方发起 LLM 兜底；
 * 规则命中直接返回、不经 LLM、不计限流。
 * </p>
 */
@Component
public class PriorityRuleEngine {

    /** 关键词 → 优先级（首发版本固定，后续如扩展走配置，需同步评估限流与一致性） */
    private static final Map<String, List<String>> RULES = Map.of(
            "P0", List.of("登录", "支付", "下单", "密码", "权限", "安全", "数据丢失", "崩溃"),
            "P1", List.of("提交", "保存", "删除", "审核", "导出", "同步"),
            "P3", List.of("提示文案", "样式", "排版", "颜色", "占位符"));

    private static final List<String> MATCH_ORDER = List.of("P0", "P1", "P3");

    /**
     * 按标题短路匹配：P0 命中即返回（高优先级优先），未命中任一规则返回 null。
     */
    public String match(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        for (String priority : MATCH_ORDER) {
            if (RULES.get(priority).stream().anyMatch(title::contains)) {
                return priority;
            }
        }
        return null;
    }
}
