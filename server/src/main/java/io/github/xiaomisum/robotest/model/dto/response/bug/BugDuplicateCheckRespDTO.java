package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 录入时重复检测响应（POST /api/project/bugs/duplicates/check，详设 3.8）。
 * similarity 为排序信号，口径随向量算子而变，阈值不对外承诺。
 */
@Data
public class BugDuplicateCheckRespDTO {

    private List<Item> list;

    @Data
    public static class Item {

        private UUID bugId;

        private String title;

        private String status;

        private Double similarity;

        /** 命中依据：向量命中的原文分块 */
        private String basis;
    }
}
