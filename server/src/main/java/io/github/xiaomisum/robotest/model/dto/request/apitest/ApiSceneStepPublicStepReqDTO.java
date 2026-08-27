package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * 添加公共步骤为场景步骤（测试场景详细设计 3.3.3）
 */
@Data
public class ApiSceneStepPublicStepReqDTO {

    @NotBlank
    private UUID publicStepId;

    /** copy / link，默认 copy */
    private String mode;

    /** 缺省追加到末尾 */
    private Integer sortOrder;

}
