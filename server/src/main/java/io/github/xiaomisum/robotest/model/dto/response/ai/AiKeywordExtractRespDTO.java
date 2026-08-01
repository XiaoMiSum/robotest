package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 关键词抽取响应（4.3 关键词模式 text 场景）：一次同步调用输出 ≤10 个关键词。
 */
@Data
public class AiKeywordExtractRespDTO {

    @NotNull(message = "keywords 不能为空")
    @Size(max = 10, message = "关键词数量不能超过 10")
    private List<String> keywords;
}
