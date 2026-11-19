package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ProposalAdopter;
import org.springframework.stereotype.Service;

/**
 * 用例优化确认承接（详设 3.2 / 4.2）：
 * 节点变更由脑图面板按 operations 在前端执行并经 WS 持久化管道落库，
 * 确认协议的后端承接器不执行任何节点变更，采纳状态由任务资源服务统一记录。
 */
@Service
public class CaseOptimizeAdopter implements ProposalAdopter {

    @Override
    public String type() {
        return Constants.AiTaskType.CASE_OPTIMIZE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        return null;
    }
}
