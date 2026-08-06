package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiKeywordExtractRespDTO;
import io.github.xiaomisum.robotest.service.ai.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * LLM 关键词抽取（4.3 遗漏点 / 4.5 回归降级共用）：一次同步结构化调用抽取 ≤10 个关键词；
 * 输入区块标题与任务指令由调用方传入，保留各业务各自的提示词口径。
 */
@Component
public class AiKeywordExtractor {

    @Resource
    private AiGatewayService aiGatewayService;

    /** 无入参关键词场景由 LLM 抽取 ≤10 个关键词（一次同步调用），返回空值过滤 + 去重后的关键词 */
    public List<String> extract(UUID userId, UUID workspaceId, UUID projectId,
                                String taskInstruction, String inputLabel, String inputText) {
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId);
        AiKeywordExtractRespDTO extract = aiGatewayService.completeStructured(
                context,
                AiFunctionType.KEYWORD_EXTRACTION,
                taskInstruction,
                inputLabel + "\n" + inputText,
                ChatCallOptions.json(),
                AiKeywordExtractRespDTO.class,
                keywords -> assertKeywords(keywords));
        return extract.getKeywords().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /** 抽取结果防御断言：每个关键词非空且 ≤20 字符（超限触发带错重试） */
    private void assertKeywords(AiKeywordExtractRespDTO extract) {
        if (extract.getKeywords() == null) {
            throw new AiOutputValidator.OutputValidationException("keywords 不能为空");
        }
        for (String keyword : extract.getKeywords()) {
            if (!StringUtils.hasText(keyword) || keyword.length() > 20) {
                throw new AiOutputValidator.OutputValidationException(
                        "keywords 中不允许空关键词或超过 20 字符的关键词");
            }
        }
    }
}
