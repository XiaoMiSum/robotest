package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 场景批量删除请求
 */
@Data
public class ApiSceneBatchDeleteReqDTO {

    @NotEmpty(message = "场景 ID 列表不能为空")
    private List<UUID> ids;

}
