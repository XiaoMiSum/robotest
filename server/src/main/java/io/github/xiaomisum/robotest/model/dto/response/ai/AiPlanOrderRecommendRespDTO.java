package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行顺序推荐结果（type=plan_order_recommend，target=计划 ID，2.2.3 / 3.4 / 4.4）。
 */
@Data
public class AiPlanOrderRecommendRespDTO {

    /** 计算时刻 test_plan.snapshot_synced_at 的列值（含 NULL），用于失效判定 */
    private String planSyncedAt;

    /** 本次计算实际使用的权重（settings 键 planOrder.weights，默认 w1=0.5/w2=0.3/w3=0.2） */
    private Map<String, Double> weights;

    /** 按推荐执行顺序排列的用例（score 降序） */
    private List<Item> items;

    @Data
    public static class Item {

        private UUID snapshotNodeId;
        private Integer order;
        private Double score;
        private Factors factors;
        /** 按需生成后回填（3.4.3，缓存复用），未生成时为 null */
        private String reason;
    }

    @Data
    public static class Factors {

        /** bug.related_case_id = 快照节点.original_node_id 的未删除缺陷数 */
        private Integer relatedBugCount;
        /** P0=1.0 / P1=0.75 / P2=0.5 / P3=0.25 / 无=0.25 */
        private Double priorityWeight;
        /** 所属文档对应模块（含子孙模块）缺陷数 ÷ 现势 case 节点数 */
        private Double moduleBugDensity;
    }
}
