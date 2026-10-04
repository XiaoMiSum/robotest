package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 用量下钻查询（GET /api/ai/usage/tasks，详设 3.7）：点击统计项跳任务详情的数据源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiUsagePageReqDTO extends PageParam {

    private LocalDate from;

    private LocalDate to;

    /** 按模型筛选 */
    private UUID modelId;

    /** 按提示词场景筛选 */
    private String scene;

    /** success / failed */
    private String status;
}
