package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 免登录分享访问的报告内容（基础设施详细设计 3.4.4）
 * 不含 ryze_snapshot 与项目归属信息，避免泄漏平台内部数据
 */
@Data
@Builder
public class ApiPublicReportRespDTO {

    private String id;

    /** 报告粒度：scene / suite */
    private String reportType;

    /** 报告名称 */
    private String name;

    private String environmentName;

    private String status;

    private Map<String, Object> summary;

    /** 按 reportType 构建的结果数据集（测试报告详细设计 2.3） */
    private Map<String, Object> result;

    private LocalDateTime createdAt;

}