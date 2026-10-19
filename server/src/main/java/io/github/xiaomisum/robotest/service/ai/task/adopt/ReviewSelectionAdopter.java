package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.ReviewSelectionHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 评审圈选建议承接（生成链详设 3.5）：委托既有 createReview 流程落库，
 * 评审快照由创建流程内建。
 */
@Service
public class ReviewSelectionAdopter implements ArtifactAdopter {

    @Resource
    private SelectionAdoptSupport support;

    @Override
    public String type() {
        return ReviewSelectionHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        return support.create(context, false);
    }
}
