package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * AI 遗漏测试点分析响应（3.3）：semanticDegraded 为 true 时前端顶部提示「当前为关键词匹配结果」。
 */
@Data
public class AiMissingPointRespDTO {

    /** 是否降级为关键词匹配（关键词版恒 true；语义升级后按 semanticSearch 能力翻转） */
    private boolean semanticDegraded;

    /** 遗漏测试点清单，最多 30 条 */
    private List<Point> points;

    @Data
    public static class Point {

        /** 建议新增用例标题，≤200 字符 */
        private String title;

        /** 遗漏原因说明 */
        private String description;

        /** 建议归属模块路径（须为候选清单中出现过的模块路径或空） */
        private String suggestedModulePath;

        /** 关联的候选用例标题（幻觉过滤后仅保留候选清单中真实存在的标题） */
        private List<String> relatedCaseTitles;
    }
}
