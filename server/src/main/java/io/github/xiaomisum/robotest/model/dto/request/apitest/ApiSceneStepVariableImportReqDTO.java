package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** 从接口导入步骤变量（测试场景详细设计 3.4.3） */
@Data
public class ApiSceneStepVariableImportReqDTO {

    @NotNull
    private UUID interfaceId;

    /** merge=合并保留已有（默认）/ replace=清空后全量导入 */
    private String strategy;

}
