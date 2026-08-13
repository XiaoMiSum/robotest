package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 归档/取消归档需求池条目请求（PUT /api/project/requirements/{id}/archive）。
 */
@Data
public class RequirementArchiveReqDTO {

    @NotNull(message = "归档状态不能为空")
    private Boolean archived;
}
