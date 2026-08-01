package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 评审检查单批 LLM 输出（每批 ≤30 用例，输出为建议数组，4.1）
 */
@Data
public class AiReviewCheckBatchDTO {

    @Valid
    @NotNull(message = "items 不能为空")
    private List<AiReviewCheckItemDTO> items;
}
