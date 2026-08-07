package io.github.xiaomisum.robotest.service.ai.support;


import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行顺序推荐评分纯函数单测（详细设计 4.4）：归一化全 0 边界、优先级权重映射、决胜规则、失效判定。
 */
class AiPlanOrderScoringTest {

    @Test
    void priorityWeight_mapsP0ToP3() {
        assertEquals(1.0, AiPlanOrderScoring.priorityWeight("P0"));
        assertEquals(0.75, AiPlanOrderScoring.priorityWeight("P1"));
        assertEquals(0.5, AiPlanOrderScoring.priorityWeight("P2"));
        assertEquals(0.25, AiPlanOrderScoring.priorityWeight("P3"));
    }

    @Test
    void priorityWeight_nullOrUnknownFallsBackTo025() {
        assertEquals(0.25, AiPlanOrderScoring.priorityWeight(null));
        assertEquals(0.25, AiPlanOrderScoring.priorityWeight("P4"));
        assertEquals(0.25, AiPlanOrderScoring.priorityWeight("high"));
    }

    @Test
    void normalize_allZero_returnsZero() {
        assertEquals(0.0, AiPlanOrderScoring.normalize(0.0, 0.0, 0.0));
        assertEquals(0.0, AiPlanOrderScoring.normalize(5.0, 5.0, 5.0));
    }

    @Test
    void normalize_mapsMinToZeroMaxToOne() {
        assertEquals(0.0, AiPlanOrderScoring.normalize(1.0, 1.0, 5.0));
        assertEquals(1.0, AiPlanOrderScoring.normalize(5.0, 1.0, 5.0));
        assertEquals(0.5, AiPlanOrderScoring.normalize(3.0, 1.0, 5.0));
    }

    @Test
    void score_isWeightedSum() {
        // 0.5 * 0.4 + 0.3 * 1.0 + 0.2 * 0.5 = 0.2 + 0.3 + 0.1 = 0.6
        assertEquals(0.6, AiPlanOrderScoring.score(0.5, 0.3, 0.2, 0.4, 1.0, 0.5));
    }

    @Test
    void compare_higherScoreFirst() {
        AiPlanOrderScoring.RankKey high = new AiPlanOrderScoring.RankKey(0.8, 0.25, 0, 0);
        AiPlanOrderScoring.RankKey low = new AiPlanOrderScoring.RankKey(0.5, 0.25, 0, 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(high, low) < 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(low, high) > 0);
    }

    @Test
    void compare_equalScore_priorityWeightDecides() {
        AiPlanOrderScoring.RankKey p0 = new AiPlanOrderScoring.RankKey(0.5, 1.0, 0, 0);
        AiPlanOrderScoring.RankKey p2 = new AiPlanOrderScoring.RankKey(0.5, 0.5, 0, 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(p0, p2) < 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(p2, p0) > 0);
    }

    @Test
    void compare_equalScoreAndPriority_relatedBugCountDecides() {
        AiPlanOrderScoring.RankKey moreBugs = new AiPlanOrderScoring.RankKey(0.5, 0.75, 5, 0);
        AiPlanOrderScoring.RankKey fewerBugs = new AiPlanOrderScoring.RankKey(0.5, 0.75, 2, 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(moreBugs, fewerBugs) < 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(fewerBugs, moreBugs) > 0);
    }

    @Test
    void compare_equalScorePriorityAndBugs_sortOrderAscendingDecides() {
        AiPlanOrderScoring.RankKey first = new AiPlanOrderScoring.RankKey(0.5, 0.75, 3, 1);
        AiPlanOrderScoring.RankKey second = new AiPlanOrderScoring.RankKey(0.5, 0.75, 3, 4);
        assertTrue(AiPlanOrderScoring.RankKey.compare(first, second) < 0);
        assertTrue(AiPlanOrderScoring.RankKey.compare(second, first) > 0);
    }

    @Test
    void compare_fullyEqual_returnsZero() {
        AiPlanOrderScoring.RankKey a = new AiPlanOrderScoring.RankKey(0.5, 0.75, 3, 2);
        AiPlanOrderScoring.RankKey b = new AiPlanOrderScoring.RankKey(0.5, 0.75, 3, 2);
        assertEquals(0, AiPlanOrderScoring.RankKey.compare(a, b));
    }

    @Test
    void isStale_bothNull_notStale() {
        assertFalse(AiPlanOrderScoring.isStale(null, null));
    }

    @Test
    void isStale_storedNull_currentValue_stale() {
        assertTrue(AiPlanOrderScoring.isStale(null, LocalDateTime.of(2026, 8, 1, 10, 0)));
    }

    @Test
    void isStale_storedValue_currentNull_stale() {
        assertTrue(AiPlanOrderScoring.isStale("2026-08-01T10:00", null));
    }

    @Test
    void isStale_equalValues_notStale() {
        LocalDateTime syncedAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        assertFalse(AiPlanOrderScoring.isStale(syncedAt.toString(), syncedAt));
    }

    @Test
    void isStale_differentValues_stale() {
        LocalDateTime stored = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime current = LocalDateTime.of(2026, 8, 2, 10, 0);
        assertTrue(AiPlanOrderScoring.isStale(stored.toString(), current));
    }
}
