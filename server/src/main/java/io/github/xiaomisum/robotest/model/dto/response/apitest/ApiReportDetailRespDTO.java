package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 报告详情（基础设施详细设计 3.4.2），result 为按 reportType 构建的结果数据集 */
@Data
@Builder
public class ApiReportDetailRespDTO {

    private String id;

    /** 报告粒度：scene / suite */
    private String reportType;

    /** 外部关联 ID（suite=任务 ID；scene=场景 ID） */
    private String externalId;

    /** 报告名称（执行时固化） */
    private String name;

    private String executionMode;

    private String status;

    private Map<String, Object> summary;

    private String environmentName;

    /** 场景报告：场景数据集；套件报告：套件数据集（测试报告详细设计 2.3） */
    private Map<String, Object> result;

    /** 未过期分享记录（shareUrl/expiresAt/shareBy）；无分享或已过期为 null（测试报告详细设计 4.2.3） */
    private ApiReportShareRespDTO share;

    private LocalDateTime createdAt;

}