package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ProposalAdopter;
import org.springframework.stereotype.Service;

/**
 * 脑图生成用例确认承接（辅助功能详设 3.4 / 4.4）：
 * 提案由脑图面板勾选后按现有节点操作插入并经 WS 持久化管道落库，
 * 确认协议只记录采纳状态；提案处置统一在脑图面板完成，后端承接器不执行任何节点变更。
 */
@Service
public class MindmapCaseGenerationAdopter implements ProposalAdopter {

    @Override
    public String type() {
        return Constants.AiTaskType.MINDMAP_CASE_GENERATION;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        return null;
    }
}
