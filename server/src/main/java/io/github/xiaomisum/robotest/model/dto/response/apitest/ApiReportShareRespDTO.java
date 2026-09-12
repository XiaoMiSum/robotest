package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 分享链接生成结果（基础设施详细设计 3.4.3） */
@Data
@Builder
public class ApiReportShareRespDTO {

    /** 相对路径分享 URL，前端拼装站点前缀 */
    private String shareUrl;

    private LocalDateTime expiresAt;

    /** 分享者 username（分享记录展示与复制文本，测试报告详细设计 4.2.3） */
    private String shareBy;

}
