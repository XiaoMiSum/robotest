package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;

/**
 * 导入与拆解的采纳落库承接（详设 4.5）：分配编号写 requirement、回写拆解记录 adopt_result 与状态，
 * 全部产物处理完成且至少采纳一条时归档原条目。与确认记录同事务，失败抛 ServiceException 整项回滚。
 */
public interface RequirementAdoptService {

    AdoptOutcome adopt(AdoptContext context);
}
