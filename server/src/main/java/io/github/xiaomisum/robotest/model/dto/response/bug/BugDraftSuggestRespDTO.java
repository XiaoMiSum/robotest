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

    /** 相似存量缺陷来源引用（向量未就绪时为空列表） */
    private List<SourceRef> sourceRefs;

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

    @Data
    public static class SourceRef {

        private String type;

        private UUID id;

        private String title;

        private String quote;
    }
}
