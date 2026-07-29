package io.github.xiaomisum.robotest.model.dto.request.bug;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * 缺陷状态变更请求 DTO（三态模型：active / resolved / closed）
 */
@Data
public class BugStatusChangeReqDTO {

    /**
     * 目标状态
     */
    @NotBlank(message = "目标状态不能为空")
    private String status;

    /**
     * 变更说明（关闭/重开时必填）
     */
    private String comment;

    /**
     * 解决方案（目标状态为 resolved 时必填）
     */
    private String resolution;

    /**
     * 原始缺陷 ID（resolution=duplicate 时必填）
     */
    private UUID duplicateOfBugId;
}
