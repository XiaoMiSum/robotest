package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Mock 启停请求（Mock服务详细设计 3.1.6） */
@Data
public class ApiMockToggleReqDTO {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

}
