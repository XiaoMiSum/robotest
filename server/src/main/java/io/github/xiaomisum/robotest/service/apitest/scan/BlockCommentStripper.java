package io.github.xiaomisum.robotest.service.apitest.scan;

/**
 * 词法块注释剥离（详细设计 6.3：强制前置步骤）。
 * <p>
 * 实测测试工程中存在位于块注释内的整段方法代码，直接交给解析器虽不会误计为方法，
 * 但残缺注释（如未闭合）会干扰解析失败分类；统一先剥离为等长空白，保持行列偏移不变。
 * 状态机正确处理：字符串 / 字符字面量内的 {@code /*}{@code *} 与行注释内的伪注释起始。
 */
public final class BlockCommentStripper {

    private enum State { CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR }

    private BlockCommentStripper() {
    }

    /**
     * 剥离全部块注释，替换为空格（换行保留），行列位置与输入一致。
     * 未闭合的块注释剥离至文件尾。
     */
    public static String strip(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length());
        State state = State.CODE;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (c == '/' && next == '*') {
                        out.append("  ");
                        state = State.BLOCK_COMMENT;
                        i++;
                    } else if (c == '/' && next == '/') {
                        out.append(c).append(next);
                        state = State.LINE_COMMENT;
                        i++;
                    } else if (c == '"') {
                        out.append(c);
                        state = State.STRING;
                    } else if (c == '\'') {
                        out.append(c);
                        state = State.CHAR;
                    } else {
                        out.append(c);
                    }
                }
                case LINE_COMMENT -> {
                    out.append(c);
                    if (c == '\n') {
                        state = State.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        out.append("  ");
                        state = State.CODE;
                        i++;
                    } else {
                        // 空白原样保留以维持行列偏移，其余字符替换为空格
                        out.append(Character.isWhitespace(c) ? c : ' ');
                    }
                }
                case STRING -> {
                    out.append(c);
                    if (c == '\\' && next != '\0') {
                        out.append(next);
                        i++;
                    } else if (c == '"') {
                        state = State.CODE;
                    }
                }
                case CHAR -> {
                    out.append(c);
                    if (c == '\\' && next != '\0') {
                        out.append(next);
                        i++;
                    } else if (c == '\'') {
                        state = State.CODE;
                    }
                }
            }
        }
        return out.toString();
    }
}
