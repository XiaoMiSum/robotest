package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.PlanSelectionHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 计划圈选建议承接（生成链详设 3.5）：委托既有 createPlan 流程落库，
 * 快照与 case_snapshot 追溯边由创建流程内建。
 */
@Service
public class PlanSelectionAdopter implements ArtifactAdopter {

    private static final String PERMISSION_CREATE = "plan:create";

    @Resource
    private SelectionAdoptSupport support;

    @Override
    public String type() {
        return PlanSelectionHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        return support.create(context, PERMISSION_CREATE, true);
    }
}
