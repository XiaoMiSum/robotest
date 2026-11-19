package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.List;
import java.util.Set;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例优化节点操作指令的共享口径（详设 3.2 / 4.2）：
 * update（原地更新，field ∈ title / priority / tags）与 add（新增子节点，nodeType 白名单不含 case）。
 * 常量与取值归一集中在此，供提案构造与测试复用。
 */
public final class CaseOptimizeOps {

    public static final String OP_UPDATE = "update";
    public static final String OP_ADD = "add";

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_PRIORITY = "priority";
    public static final String FIELD_TAGS = "tags";

    public static final Set<String> UPDATE_FIELDS = Set.of(FIELD_TITLE, FIELD_PRIORITY, FIELD_TAGS);
    public static final Set<String> ADD_NODE_TYPES = Set.of(
            Constants.NodeType.PRECONDITION, Constants.NodeType.STEP,
            Constants.NodeType.EXPECTED, Constants.NodeType.NORMAL);
    public static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");

    public static final int MAX_TAGS = 5;
    public static final int MAX_TITLE = 500;

    private CaseOptimizeOps() {
    }

    /** update 建议值归一：title 单段文本、priority 白名单、tags 列表截断；空白视为无建议返回 null */
    public static Object normalizeSuggested(String field, Object raw) {
        if (FIELD_TITLE.equals(field)) {
            String title = trimToNull(asText(raw));
            return title == null || title.length() > MAX_TITLE ? null : title;
        }
        if (FIELD_PRIORITY.equals(field)) {
            String priority = trimToNull(asText(raw));
            return priority != null && PRIORITIES.contains(priority) ? priority : null;
        }
        if (FIELD_TAGS.equals(field)) {
            List<String> tags = stringList(raw, MAX_TAGS);
            return tags.isEmpty() ? null : tags;
        }
        return null;
    }

    private static String asText(Object raw) {
        if (raw instanceof List<?> list) {
            return list.isEmpty() ? null : String.valueOf(list.getFirst());
        }
        return raw == null ? null : String.valueOf(raw);
    }
}
