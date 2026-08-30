package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

/** 生成分享链接（基础设施详细设计 3.4.3）；expiresInDays 缺省 7 天 */
@Data
public class ApiReportShareReqDTO {

    private Integer expiresInDays;

}
