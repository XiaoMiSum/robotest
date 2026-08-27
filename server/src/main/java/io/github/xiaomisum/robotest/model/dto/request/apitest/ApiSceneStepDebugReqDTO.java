package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 单步骤调试（测试场景详细设计 3.6.3）
 */
@Data
public class ApiSceneStepDebugReqDTO {

    /** 缺省使用场景默认环境 */
    @NotNull(message = "调试环境不能为空")
    private UUID environmentId;

}
