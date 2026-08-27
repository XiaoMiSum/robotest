package io.github.xiaomisum.robotest.service.apitest;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5 位 Cron 解析支持（定时任务详细设计 4.1） */
class CronSupportTest {

    @Test
    void parseAcceptsFiveFieldExpression() {
        assertNotNull(CronSupport.parse("*/5 * * * *"));
        assertNotNull(CronSupport.parse("0 2 * * 1-5"));
    }

    @Test
    void parseRejectsInvalidExpressionWithNull() {
        assertNull(CronSupport.parse("not a cron"));
        assertNull(CronSupport.parse("61 * * * *"));
    }

    @Test
    void nextNReturnsSequentialFireTimes() {
        LocalDateTime after = LocalDateTime.of(2026, 8, 25, 10, 0);
        List<LocalDateTime> times = CronSupport.nextN("0 2 * * *", after, 3);
        assertEquals(3, times.size());
        assertEquals(LocalDateTime.of(2026, 8, 26, 2, 0), times.get(0));
        assertEquals(LocalDateTime.of(2026, 8, 27, 2, 0), times.get(1));
        assertEquals(LocalDateTime.of(2026, 8, 28, 2, 0), times.get(2));
        // 序列严格递增
        assertTrue(times.get(0).isBefore(times.get(1)));
    }

    @Test
    void nextNReturnsEmptyForInvalidExpression() {
        assertTrue(CronSupport.nextN("bad", LocalDateTime.now(), 3).isEmpty());
    }

    @Test
    void describeMapsPresetAndPassesThroughCustom() {
        assertEquals("每 5 分钟", CronSupport.describe("*/5 * * * *"));
        assertEquals("每天凌晨 2:00", CronSupport.describe("0 2 * * *"));
        assertEquals("工作日凌晨 2:00", CronSupport.describe("0 2 * * 1-5"));
        assertEquals("7 3 29 2 *", CronSupport.describe("7 3 29 2 *"));
    }

    @Test
    void nextDelegatesToSpringCronExpression() {
        CronExpression expression = CronSupport.parse("*/15 * * * *");
        assertNotNull(expression);
        LocalDateTime after = LocalDateTime.of(2026, 8, 25, 10, 7);
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 15),
                CronSupport.next(expression, after));
    }
}
