package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** 批量删除接口（接口管理详细设计 3.1.10），存在引用时整体拒绝 */
@Data
public class ApiInterfaceBatchDeleteReqDTO {

    @NotEmpty(message = "接口 ID 列表不能为空")
    private List<UUID> ids;
}
