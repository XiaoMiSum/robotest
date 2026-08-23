package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApiEnvironmentSortReqDTO {

    @NotNull(message = "排序序号不能为空")
    private Integer sortOrder;
}
