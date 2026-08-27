package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 通过接口快速创建步骤（测试场景详细设计 3.3.2）
 */
@Data
public class ApiSceneStepQuickCreateReqDTO {

    @NotNull
    private UUID interfaceId;

    /** copy=快照独立（默认）/ link=跟随源变更 */
    private String mode;

    /** 是否自动导入接口级变量，默认 true，仅对接口定义本身生成的步骤生效 */
    private Boolean importInterfaceVariables;

}
