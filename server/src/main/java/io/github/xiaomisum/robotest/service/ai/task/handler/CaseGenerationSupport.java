package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.abbreviate;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 脑图生成用例共用支撑（辅助功能详设 3.4 / 4.4）：需求快照、提示词上下文与模型输出清洗。
 * 全部为无副作用静态方法，提交期快照与执行期解析读同一份口径。
 */
public final class CaseGenerationSupport {

    /** 需求描述进入提示词的截断长度（why：导入正文可整篇落库） */
    private static final int MAX_DESCRIPTION_CHARS = 3000;
    /** 单条用例的属性条数上限，防止模型输出撑爆上下文与脑图树 */
    private static final int MAX_ITEMS = 10;
    private static final int MAX_TAGS = 5;
    private static final int MAX_EXISTING_TITLES = 50;
    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");
    private static final String DEFAULT_PRIORITY = "P2";

    private CaseGenerationSupport() {
    }

    /** 提交期冻结需求关键字段；执行期只读快照，需求后续变更不影响本次生成 */
    public static Map<String, Object> snapshotOf(Requirement item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", String.valueOf(item.getId()));
        snapshot.put("code", nvl(item.getCode()));
        snapshot.put("title", nvl(item.getTitle()));
        snapshot.put("systemVersion", nvl(item.getSystemVersion()));
        snapshot.put("priority", nvl(item.getPriority()));
        snapshot.put("status", nvl(item.getStatus()));
        snapshot.put("description", nvl(item.getDescription()));
        snapshot.put("updatedAt", item.getUpdatedAt() == null ? null : String.valueOf(item.getUpdatedAt()));
        return snapshot;
    }

    /** 需求上下文（基于快照，执行期与提交期一致） */
    public static String requirementContext(Map<String, Object> snapshot) {
        Map<String, Object> source = snapshot == null ? Map.of() : asMap(snapshot);
        String id = trimToNull(asString(source.get("id")));
        if (id == null) {
            return "（需求快照缺失）";
        }
        StringBuilder builder = new StringBuilder("来源需求（已冻结快照，sourceRefs 只引用此 ID）：\n");
        builder.append("- [").append(id).append("] ")
                .append(nvl(asString(source.get("code")))).append(" | ")
                .append(nvl(asString(source.get("title")))).append(" | ")
                .append(nvl(asString(source.get("priority")))).append(" | ")
                .append(nvl(asString(source.get("status")))).append('\n');
        String version = trimToNull(asString(source.get("systemVersion")));
        if (version != null) {
            builder.append("  版本：").append(version).append('\n');
        }
        String description = trimToNull(asString(source.get("description")));
        if (description != null) {
            builder.append("  描述：").append(abbreviate(description, MAX_DESCRIPTION_CHARS)).append('\n');
        }
        return builder.toString();
    }

    /** 当前文档上下文：已有用例标题仅作去重参考，不覆盖既有节点 */
    public static String documentContext(String documentName, Collection<String> existingCaseTitles) {
        StringBuilder builder = new StringBuilder("当前脑图文档：")
                .append(nvl(documentName)).append('\n');
        List<String> titles = existingCaseTitles == null ? List.of()
                : existingCaseTitles.stream().limit(MAX_EXISTING_TITLES).toList();
        if (titles.isEmpty()) {
            builder.append("已存在用例：（暂无）\n");
        } else {
            builder.append("已存在用例标题（避免生成标题完全相同的重复用例）：\n");
            titles.forEach(title -> builder.append("- ").append(title).append('\n'));
        }
        return builder.toString();
    }

    /**
     * 提案内容清洗：属性转字符串数组并截齐步骤 / 预期，来源引用限定快照需求；
     * 无有效来源返回 null，由调用方丢弃该提案（4.4）。
     */
    public static Map<String, Object> sanitizeContent(Map<String, Object> raw,
            Map<UUID, Requirement> requirementById) {
        Map<String, Object> source = asMap(raw);
        List<String> steps = stringList(source.get("steps"), MAX_ITEMS);
        List<String> expected = stringList(source.get("expected"), MAX_ITEMS);
        int paired = Math.min(steps.size(), expected.size());

        List<Map<String, Object>> sourceRefs = CaseAssistSupport.sourceRefsOf(source, requirementById, true);
        if (sourceRefs.isEmpty()) {
            return null;
        }

        String priority = trimToNull(asString(source.get("priority")));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("priority", priority != null && PRIORITIES.contains(priority) ? priority : DEFAULT_PRIORITY);
        content.put("precondition", stringList(source.get("precondition"), MAX_ITEMS));
        content.put("steps", new ArrayList<>(steps.subList(0, paired)));
        content.put("expected", new ArrayList<>(expected.subList(0, paired)));
        content.put("tags", stringList(source.get("tags"), MAX_TAGS));
        content.put("sourceRefs", sourceRefs);
        return content;
    }
}
