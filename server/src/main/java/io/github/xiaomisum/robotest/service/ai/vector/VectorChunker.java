package io.github.xiaomisum.robotest.service.ai.vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块器（详设 4.4「分块重嵌」）：结构分块 —— 标题行独立成块、正文行按段落聚合，
 * 单块超长再按固定窗口重叠回切（取值经用户裁决：500 字窗口 / 50 字重叠）。
 */
public final class VectorChunker {

    /** 单块字符上限：兼顾主流向量模型输入窗口与检索粒度 */
    static final int MAX_CHARS = 500;

    /** 超长回切的窗口重叠，保留跨边界的语义衔接 */
    static final int OVERLAP_CHARS = 50;

    /** 短于此长度的行视为标题，独立成块，避免正文聚合时吞掉标题语境 */
    static final int TITLE_MAX_CHARS = 40;

    private VectorChunker() {
    }

    public static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > MAX_CHARS) {
                flush(current, chunks);
                chunks.addAll(windowSplit(line));
                continue;
            }
            boolean title = line.length() <= TITLE_MAX_CHARS && i < lines.size() - 1;
            if (title) {
                flush(current, chunks);
                chunks.add(line);
                continue;
            }
            if (current.isEmpty()) {
                current.append(line);
            } else if (current.length() + 1 + line.length() <= MAX_CHARS) {
                current.append('\n').append(line);
            } else {
                flush(current, chunks);
                current.append(line);
            }
        }
        flush(current, chunks);
        return chunks;
    }

    /** 超长行按 MAX_CHARS 开窗、OVERLAP_CHARS 重叠回切，保证边界语义不丢 */
    private static List<String> windowSplit(String line) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + MAX_CHARS, line.length());
            parts.add(line.substring(start, end));
            if (end >= line.length()) {
                break;
            }
            start = end - OVERLAP_CHARS;
        }
        return parts;
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
