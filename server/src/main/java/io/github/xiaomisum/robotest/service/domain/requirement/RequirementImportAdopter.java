package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 导入产物承接注册（详设 3.6.1 requirement_import → 需求模块采纳服务）：
 * 委托 RequirementAdoptService 的落库事务（4.5）。
 */
@Component
public class RequirementImportAdopter implements ArtifactAdopter {

    @Resource
    private RequirementAdoptService requirementAdoptService;

    @Override
    public String type() {
        return "requirement_import";
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        return requirementAdoptService.adopt(context);
    }
}
