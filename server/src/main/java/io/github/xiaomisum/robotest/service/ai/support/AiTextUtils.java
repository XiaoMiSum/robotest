package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * AI 文本工具：与 PromptAssembler 估算口径一致的文本裁剪、关键词降级模式共用的分词。
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

    /**
     * 关键词分词：按非字母数字切分，保留 ≥2 字符片段（中英文通用，过滤单字噪声），
     * 返回有序去重词集。语义查重关键词降级与聚类降级共用同一口径，避免两套分词漂移。
     */
    public static Set<String> tokenizeKeywords(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : text.toLowerCase().split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * 聚类降级分词：CJK（汉字）按单字切分、非 CJK 字母数字按整词保留（≥2 字符），
     * 返回有序去重词集。中文标题无空格分隔，整词切分会使整句成为单个 token 导致
     * 重叠系数恒为 0（详见详细设计 4.3 降级模式）；单字是中文的最小语义单元，
     * 与查重降级（title ILIKE 需要整词）口径分离，避免互相污染。
     */
    public static Set<String> tokenizeKeywordsForClustering(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        StringBuilder word = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                flushWord(word, tokens);
                tokens.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                word.append(c);
            } else {
                flushWord(word, tokens);
            }
        }
        flushWord(word, tokens);
        return tokens;
    }

    private static void flushWord(StringBuilder word, Set<String> tokens) {
        if (word.length() >= 2) {
            tokens.add(word.toString());
        }
        word.setLength(0);
    }
}
