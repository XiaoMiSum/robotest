package io.github.xiaomisum.robotest.model.dto.request.trace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 人工新建追溯边（POST /api/project/trace/edges，详设 3.5）。
 */
@Data
public class TraceEdgeCreateReqDTO {

    /** derivation / case_snapshot */
    @NotBlank(message = "边类型不能为空")
    private String edgeType;

    @NotBlank(message = "源节点类型不能为空")
    private String sourceType;

    @NotNull(message = "源节点 ID 不能为空")
    private UUID sourceId;

    @NotBlank(message = "目标节点类型不能为空")
    private String targetType;

    @NotNull(message = "目标节点 ID 不能为空")
    private UUID targetId;

    /** 引用时目标内容的版本标识，无版本概念的节点类型可不传 */
    @Size(max = 64, message = "目标版本不能超过 64 字符")
    private String targetVersion;
}
