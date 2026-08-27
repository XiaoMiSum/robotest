package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

/** 生成分享链接（基础设施详细设计 3.4.3）；expiresInDays 缺省取项目设置 report.share.expire-days */
@Data
public class ApiReportShareReqDTO {

    private Integer expiresInDays;

}
