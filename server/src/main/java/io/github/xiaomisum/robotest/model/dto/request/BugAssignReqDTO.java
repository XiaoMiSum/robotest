package io.github.xiaomisum.robotest.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 缺陷指派请求 DTO
 */
@Data
public class BugAssignReqDTO {

    /**
     * 处理人用户 ID
     */
    @NotNull(message = "处理人不能为空")
    private UUID assigneeId;
}
