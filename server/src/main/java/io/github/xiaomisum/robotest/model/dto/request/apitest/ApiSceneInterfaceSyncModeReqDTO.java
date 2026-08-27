package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 切换关联同步模式（测试场景详细设计 3.2.4） */
@Data
public class ApiSceneInterfaceSyncModeReqDTO {

    @NotBlank
    private String syncMode;

}
