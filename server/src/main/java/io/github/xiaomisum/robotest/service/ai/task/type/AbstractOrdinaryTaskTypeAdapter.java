package io.github.xiaomisum.robotest.service.ai.task.type;

import java.util.List;
import java.util.Map;

/** 十一类普通 Agent 任务共享的预算、提示词与兜底结构化解析。 */
public abstract class AbstractOrdinaryTaskTypeAdapter implements TaskTypeAdapter {

    protected abstract String defaultPrompt();

    protected abstract String defaultProposalKind();

    @Override
    public final Map<String, Object> defaultBudget() {
        return STANDARD_BUDGET;
    }

    @Override
    public final String systemPrompt() {
        return defaultPrompt();
    }

    @Override
    public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
        return ProposalDrafts.parse(finalOutput, defaultProposalKind());
    }
}
