package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 触发场景执行（测试场景详细设计 3.6.1 + 基础设施详细设计 3.2.1）
 */
@Data
public class ApiSceneExecuteReqDTO {

    /** 缺省使用场景默认环境 */
    private UUID environmentId;

    /** 运行时变量覆盖，优先级最高（测试场景详细设计 4.1） */
    private Map<String, Object> variableOverrides;

    /**
     * 触发来源标记：HTTP 入口不传按 manual 落库；
     * 定时任务链路传 scheduled，使执行记录可区分自动执行
     */
    private String triggerType;

}
