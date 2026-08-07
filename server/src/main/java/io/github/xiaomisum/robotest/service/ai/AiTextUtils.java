package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;

/**
 * AI 文本工具：与 PromptAssembler 估算口径一致的文本裁剪。
 */
public final class AiTextUtils {

    private AiTextUtils() {
    }

    /**
     * 将文本截断至指定 token 预算内（估算口径与 {@link PromptAssembler#estimateTokens} 一致）；
     * 超出预算时裁剪到预算内最近位置并追加省略号「…」，空文本/null 原样返回。
     */
    public static String truncateToTokenBudget(String text, int tokenBudget) {
        if (text == null || PromptAssembler.estimateTokens(text) <= tokenBudget) {
            return text;
        }
        int ascii = 0;
        int other = 0;
        int cut = text.length();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 128) {
                ascii++;
            } else {
                other++;
            }
            if (ascii / 4 + other > tokenBudget) {
                cut = i;
                break;
            }
        }
        // 回退到预算内最近位置（避免 ascii 取整边界越过预算）
        while (cut > 0 && PromptAssembler.estimateTokens(text.substring(0, cut)) > tokenBudget) {
            cut--;
        }
        return text.substring(0, cut) + "…";
    }
}
