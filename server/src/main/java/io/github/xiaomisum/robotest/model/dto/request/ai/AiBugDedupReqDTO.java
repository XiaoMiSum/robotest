package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 缺陷语义查重请求（3.2）：title 必填且 ≤ 300，reproSteps 可空，excludeBugId 编辑既有缺陷时排除自身
 */
@Data
public class AiBugDedupReqDTO {

    @NotBlank(message = "缺陷标题不能为空")
    @Size(max = 300, message = "缺陷标题不能超过 300 字符")
    private String title;

    private String reproSteps;

    private UUID excludeBugId;
}
