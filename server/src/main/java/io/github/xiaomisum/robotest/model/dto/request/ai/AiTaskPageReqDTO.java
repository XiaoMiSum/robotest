package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 任务列表查询（GET /api/ai/tasks，详设 3.6.3）：type / status 精确筛选，
 * 项目范围经 X-Active-Project 头过滤，未附带则返回本人提交的任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskPageReqDTO extends PageParam {

    /** 任务类型筛选（详设 3.6.1 枚举值） */
    private String type;

    /** 状态筛选：pending / running / succeeded / failed / cancelled */
    private String status;
}
