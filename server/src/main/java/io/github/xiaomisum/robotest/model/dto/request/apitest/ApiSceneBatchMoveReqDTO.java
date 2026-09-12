package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 批量移动场景至目标模块（测试场景详细设计 3.1.7），moduleId 空为未分组 */
@Data
public class ApiSceneBatchMoveReqDTO {

    @NotEmpty(message = "场景 ID 列表不能为空")
    private List<UUID> ids;

    private UUID moduleId;
}