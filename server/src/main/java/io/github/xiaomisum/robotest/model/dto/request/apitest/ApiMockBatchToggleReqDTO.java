package io.github.xiaomisum.robotest.model.dto.request.apitest;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Mock 批量启停请求（Mock服务详细设计 3.1.11），逐条校验、缺失跳过 */
@Data
public class ApiMockBatchToggleReqDTO {

    private List<UUID> ids;

    private Boolean enabled;

}
