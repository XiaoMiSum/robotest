package io.github.xiaomisum.robotest.service.ai.support;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 执行顺序推荐评分纯函数（详细设计 4.4）：确定性计算，便于单元测试与结果复现。
 *
 * <p>评分模型：score = w1·norm(relatedBugCount) + w2·priorityWeight + w3·norm(moduleBugDensity)。
 * min-max 归一化在全 0 时取 0；同分按 priorityWeight、relatedBugCount、快照 sort_order 依次决胜。</p>
 */
public final class AiPlanOrderScoring {

    private AiPlanOrderScoring() {
    }

    /**
     * 优先级权重映射：P0=1.0 / P1=0.75 / P2=0.5 / P3=0.25 / 无=0.25
     */
    public static double priorityWeight(String priority) {
        if (priority == null) {
            return 0.25;
        }
        return switch (priority) {
            case "P0" -> 1.0;
            case "P1" -> 0.75;
            case "P2" -> 0.5;
            case "P3" -> 0.25;
            default -> 0.25;
        };
    }

    /**
     * 项目内 min-max 归一化；max <= min（全 0 或全部相等）时取 0
     */
    public static double normalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    /**
     * 综合评分：w1·norm(relatedBugCount) + w2·priorityWeight + w3·norm(moduleBugDensity)
     */
    public static double score(double w1, double w2, double w3,
                               double bugNorm, double priorityWeight, double densityNorm) {
        return w1 * bugNorm + w2 * priorityWeight + w3 * densityNorm;
    }

    /**
     * 排序决胜键：score 降序 → priorityWeight 降序 → relatedBugCount 降序 → 快照 sort_order 升序
     */
    public record RankKey(double score, double priorityWeight, int relatedBugCount, int sortOrder) {

        /**
         * 决胜键比较：score/priorityWeight/relatedBugCount 降序，快照 sort_order 升序.
         *
         * @param a 比较键 a
         * @param b 比较键 b
         * @return 排序权重比较结果
         */
        public static int compare(RankKey a, RankKey b) {
            int c = Double.compare(b.score, a.score);
            if (c != 0) {
                return c;
            }
            c = Double.compare(b.priorityWeight, a.priorityWeight);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(b.relatedBugCount, a.relatedBugCount);
            if (c != 0) {
                return c;
            }
            return Integer.compare(a.sortOrder, b.sortOrder);
        }
    }

    /**
     * 失效判定：result.planSyncedAt 与当前 test_plan.snapshot_synced_at 不相等（含 NULL 口径）即 stale
     */
    public static boolean isStale(String storedPlanSyncedAt, LocalDateTime currentSnapshotSyncedAt) {
        String current = currentSnapshotSyncedAt == null ? null : currentSnapshotSyncedAt.toString();
        return !Objects.equals(storedPlanSyncedAt, current);
    }
}
