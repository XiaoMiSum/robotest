package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新接口定义（接口管理详细设计 3.1.4），携带乐观锁版本号
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ApiInterfaceUpdateReqDTO extends ApiInterfaceCreateReqDTO {

    @NotNull(message = "变更版本号不能为空")
    private Integer changeVersion;
}
