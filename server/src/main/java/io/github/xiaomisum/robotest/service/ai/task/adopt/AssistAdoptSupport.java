package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 辅助功能承接共用支撑：任务入参读取（target 缺省回退任务入参）与采纳后的项目动态留痕。
 */
@Component
public class AssistAdoptSupport {

    @Resource
    private ProjectActivityService projectActivityService;

    /** 任务入参：缺失时给空表，供承接侧按 target 缺省逐字段回退 */
    public static Map<String, Object> taskInput(AdoptContext context) {
        Map<String, Object> input = context.task() == null ? null : context.task().getInput();
        return input == null ? Map.of() : input;
    }

    /** 采纳留痕：确认记录由总册确认入口写，此处补项目动态时间线 */
    public void record(AdoptContext context, String resourceType, UUID resourceId, String resourceName,
            String action, String summary) {
        projectActivityService.record(context.projectId(), context.operatorId(), resourceType, resourceId,
                resourceName, action, summary);
    }
}
