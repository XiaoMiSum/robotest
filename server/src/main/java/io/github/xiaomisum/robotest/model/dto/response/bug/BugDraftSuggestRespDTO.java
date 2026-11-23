package io.github.xiaomisum.robotest.model.dto.response.bug;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 新建缺陷实时建议响应（POST /api/project/bugs/analysis/suggest，详设 3.5）。
 * 建议仅以徽标呈现、一键采纳，不写入任何数据。
 */
@Data
public class BugDraftSuggestRespDTO {

    /** 优化后标题建议 */
    private FieldSuggestion title;

    /** 重现步骤建议（含执行步骤 / 实际结果 / 期望结果三段） */
    private FieldSuggestion reproSteps;

    /** 分类与关键词建议（逐字段独立，可缺省） */
    private Suggestions suggestions;

    /** 同一次建议检索出的疑似重复缺陷（向量未就绪或无命中时为空） */
    private List<DuplicateItem> duplicates;

    @Data
    public static class DuplicateItem {

        private UUID bugId;

        private String title;

        private String status;

        /** 仅作排序信号，阈值不对外承诺 */
        private Double similarity;

        /** 命中依据：向量命中的原文分块 */
        private String basis;
    }

    @Data
    public static class FieldSuggestion {

        private String value;

        private String reason;
    }

    @Data
    public static class KeywordsSuggestion {

        private List<String> value;

        private String reason;
    }

    @Data
    public static class RelatedCaseSuggestion {

        /** 建议关联的用例节点 id（候选用例之外不产出） */
        private String value;

        private String caseTitle;

        private String reason;
    }

    @Data
    public static class Suggestions {

        private FieldSuggestion bugType;

        private FieldSuggestion severity;

        private FieldSuggestion priority;

        private FieldSuggestion moduleId;

        private KeywordsSuggestion keywords;

        private RelatedCaseSuggestion relatedCase;
    }
}
