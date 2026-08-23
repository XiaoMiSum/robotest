package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 批量移动接口至目标模块（接口管理详细设计 3.1.9），moduleId 空为未分组 */
@Data
public class ApiInterfaceBatchMoveReqDTO {

    @NotEmpty(message = "接口 ID 列表不能为空")
    private List<UUID> ids;

    private UUID moduleId;
}
