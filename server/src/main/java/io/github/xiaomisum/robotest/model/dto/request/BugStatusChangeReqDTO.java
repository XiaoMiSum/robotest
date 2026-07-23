package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 缺陷状态变更请求 DTO
 */
@Data
public class BugStatusChangeReqDTO {

    /**
     * 目标状态
     */
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /**
     * 变更说明（重开/关闭时必填）
     */
    private String comment;
}
