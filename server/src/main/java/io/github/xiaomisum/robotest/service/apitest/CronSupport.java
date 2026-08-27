package io.github.xiaomisum.robotest.service.apitest;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 5 位分钟级 Cron 解析支持（定时任务详细设计 4.1）；
 * Spring CronExpression 为 6 位（秒精度），统一补 "0 " 前缀换算，零新增依赖
 */
final class CronSupport {

    /** 预设表达式 → 中文描述（定时任务详细设计 4.1 预设表 + 交互设计常用预设） */
    private static final Map<String, String> PRESET_DESCRIPTIONS = Map.of(
            "0 * * * *", "每小时整点",
            "*/5 * * * *", "每 5 分钟",
            "0 2 * * *", "每天凌晨 2:00",
            "0 2 * * 1", "每周一凌晨 2:00",
            "0 2 1 * *", "每月 1 号凌晨 2:00",
            "0 2 * * 1-5", "工作日凌晨 2:00"
    );

    private CronSupport() {
    }

    /** 解析失败返回 null，由调用方决定抛错或标记 invalid */
    static CronExpression parse(String fiveFieldCron) {
        try {
            return CronExpression.parse("0 " + fiveFieldCron.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static LocalDateTime next(CronExpression expression, LocalDateTime after) {
        return expression.next(after);
    }

    /** 截取 after 起的 count 次触发时间；表达式非法返回空列表 */
    static List<LocalDateTime> nextN(String fiveFieldCron, LocalDateTime after, int count) {
        CronExpression expression = parse(fiveFieldCron);
        if (expression == null) {
            return List.of();
        }
        List<LocalDateTime> result = new ArrayList<>(count);
        LocalDateTime cursor = after;
        for (int i = 0; i < count; i++) {
            cursor = expression.next(cursor);
            if (cursor == null) {
                break;
            }
            result.add(cursor);
        }
        return result;
    }

    /** 预设表达式给中文描述，自定义原样返回 */
    static String describe(String fiveFieldCron) {
        return PRESET_DESCRIPTIONS.getOrDefault(fiveFieldCron.trim(), fiveFieldCron.trim());
    }

}
