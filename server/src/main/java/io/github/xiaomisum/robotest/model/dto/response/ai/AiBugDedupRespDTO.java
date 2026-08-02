package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 缺陷语义查重响应（3.2）：semanticDegraded 为 true 时前端展示降级提示条（关键词匹配结果）
 */
@Data
public class AiBugDedupRespDTO {

    /** 是否降级为关键词匹配（Embedding 未配置/调用失败/降级状态时为 true） */
    private boolean semanticDegraded;

    private List<Item> items;

    @Data
    public static class Item {

        private UUID bugId;

        private String title;

        private String status;

        private String assigneeName;

        /** 余弦相似度；降级模式下为 null（前端不展示相似度徽标） */
        private Double similarity;
    }
}
