package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 报告列表条目（基础设施详细设计 3.4.1 + 测试报告详细设计 3.1） */
@Data
@Builder
public class ApiReportPageItemRespDTO {

    private String id;

    /** 报告粒度：scene / suite */
    private String reportType;

    /** 外部关联 ID（suite=任务 ID；scene=场景 ID） */
    private String externalId;

    /** 报告名称（场景报告：场景名 + 时间戳；套件报告：任务名 + 时间戳） */
    private String name;

    /** 场景报告时场景名称快照；套件报告为 null */
    private String sceneName;

    private String executionMode;

    /** success / failed / partial */
    private String status;

    /** 场景报告 {total, passed, failed, skipped, durationMs}；套件报告含场景级汇总 */
    private Map<String, Object> summary;

    private String environmentName;

    private LocalDateTime createdAt;

}