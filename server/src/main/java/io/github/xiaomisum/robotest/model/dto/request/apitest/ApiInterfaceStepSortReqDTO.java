package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 公共步骤排序（接口管理详细设计 3.2.4） */
@Data
public class ApiInterfaceStepSortReqDTO {

    @NotNull(message = "排序序号不能为空")
    private Integer sortOrder;
}
