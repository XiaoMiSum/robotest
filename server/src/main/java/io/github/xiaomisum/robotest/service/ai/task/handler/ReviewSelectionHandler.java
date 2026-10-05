package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;

/**
 * 评审圈选建议处理器（生成链详设 3.1 类型 B）：单轮推荐，不接受 roundCount；
 * 确认采纳委托既有 createReview 流程（SelectionAdoptServiceImpl）。
 */
@Component
public class ReviewSelectionHandler extends AbstractSelectionHandler {

    public static final String TYPE = "review_selection";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试评审专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                从候选用例中推荐应纳入本次评审的子集：
                1. 优先选择新增需求覆盖、高风险或跨模块的用例，理由必须基于需求与用例内容，不得臆造；
                2. 推荐数量占候选的 30%~80%，候选不足 10 条时可全部推荐；不得推荐范围外的用例。
                输出结构（JSON 对象，artifacts 即产物数组）：
                {"artifacts":[{"key":"sel-1","content":{"items":[{"caseId":"…","title":"…","reason":"…"}],"round":null}}]}

                圈选范围上下文：
                {{scopeContext}}
                """;
    }

    /** 评审为单轮，禁止传入 roundCount（详设 3.2 输入裁决） */
    @Override
    protected Integer parseRoundCount(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("roundCount");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        return null;
    }

    @Override
    protected String selectionKind() {
        return Constants.AiArtifactKind.REVIEW_SELECTION;
    }

    @Override
    protected String titlePrefix() {
        return "评审圈选建议";
    }
}
