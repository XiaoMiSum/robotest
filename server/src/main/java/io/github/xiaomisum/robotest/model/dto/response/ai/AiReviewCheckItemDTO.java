package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 评审一键检查单条建议（result.items 元素，2.2.1）
 */
@Data
public class AiReviewCheckItemDTO {

    @NotBlank(message = "snapshotNodeId 不能为空")
    private String snapshotNodeId;
    @NotBlank(message = "dimension 不能为空")
    private String dimension;
    @NotBlank(message = "suggestion 不能为空")
    private String suggestion;
}
