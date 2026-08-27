package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 执行状态轮询响应（基础设施详细设计 3.2.2），前端 2s 间隔轮询至终态 */
@Data
@Builder
public class ApiExecutionStatusRespDTO {

    private String id;

    private String sceneId;

    private String sceneName;

    /** pending / running / success / failed / error / cancelled / timeout */
    private String status;

    private String executionMode;

    private String triggerType;

    private LocalDateTime executedAt;

    private Integer durationMs;

    /** 失败原因（error/cancelled 时） */
    private String errorMessage;

    /** 执行完成后指向报告，报告查询迭代交付前为空 */
    private String reportId;

}
