package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;

/**
 * 计划圈选建议处理器（生成链详设 3.1 类型 B）：按轮次分组推荐，roundCount 必传且 ∈ [1,10]；
 * 确认采纳委托既有 createPlan 流程（SelectionAdoptServiceImpl）。
 */
@Component
public class PlanSelectionHandler extends AbstractSelectionHandler {

    public static final String TYPE = "plan_selection";
    private static final int MAX_ROUNDS = 10;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return """
                你是资深测试计划专家，只输出 JSON，不输出解释或代码块标记以外的任何文字。
                按轮次从候选用例中推荐应纳入本次计划的子集：
                1. 每条给出 round（1 到 {{roundCount}} 的整数），同轮内按执行依赖排序，跨轮按先核心后回归分组；
                2. 理由必须基于需求与用例内容，不得臆造；不得推荐范围外的用例；
                3. 推荐数量占候选的 30%~80%，候选不足 10 条时可全部推荐。
                输出结构（JSON 对象，artifacts 即产物数组，items[].round 为轮次）：
                {"artifacts":[{"key":"sel-1","content":{"items":[{"caseId":"…","title":"…","reason":"…","round":1}],"round":null}}]}

                圈选范围上下文：
                {{scopeContext}}
                """;
    }

    /** 计划必传轮次且 ∈ [1,10]（详设 3.2 输入裁决，越界按 1000018206） */
    @Override
    protected Integer parseRoundCount(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("roundCount");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        int rounds;
        try {
            rounds = Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        if (rounds < 1 || rounds > MAX_ROUNDS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        return rounds;
    }

    @Override
    protected String selectionKind() {
        return Constants.AiArtifactKind.PLAN_SELECTION;
    }

    @Override
    protected String titlePrefix() {
        return "计划圈选建议";
    }
}
