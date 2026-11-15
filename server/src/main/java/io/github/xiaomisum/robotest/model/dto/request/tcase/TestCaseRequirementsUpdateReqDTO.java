package io.github.xiaomisum.robotest.model.dto.request.tcase;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 用例节点关联需求全量覆盖请求；空数组表示清空关联。
 */
@Data
public class TestCaseRequirementsUpdateReqDTO {

    @NotNull(message = "需求列表不能为空")
    private List<UUID> requirementIds;
}
