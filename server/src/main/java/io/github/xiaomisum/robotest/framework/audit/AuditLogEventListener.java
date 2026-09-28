package io.github.xiaomisum.robotest.framework.audit;

import io.github.xiaomisum.robotest.model.entity.admin.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import xyz.migoo.framework.common.observability.AuditLogEvent;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计落库监听器：订阅框架 {@link AuditLogEvent}（{@code @AuditLog} 标注方法执行后由框架发布），
 * 组装 {@code sys_audit_log} 记录并交由 {@link AuditLogWriter} 写入。
 *
 * <p>组装口径见《审计查询详细设计说明书》4.3：{@code action} 形如 {@code CREATE:User}，
 * {@code operator} 形如 {@code id(username)}，{@code entityId} 取入参 JSON 数组中首个 UUID 元素，
 * {@code changes} 存 {@code {"params":[...]}}。失败记录 {@code sys_audit_log} 无对应列，仅落成功记录。</p>
 */
@Slf4j
@Component
public class AuditLogEventListener {

    /** operator 形如 {@code 550e8400-e29b-41d4-a716-446655440000(admin)} */
    private static final Pattern OPERATOR_PATTERN =
            Pattern.compile("^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\((.*)\\)$");

    private final AuditLogWriter auditLogWriter;

    public AuditLogEventListener(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @EventListener
    public void onAuditLogEvent(AuditLogEvent event) {
        try {
            if (!event.success()) {
                return;
            }
            AuditLog record = toRecord(event);
            if (record == null) {
                return;
            }
            auditLogWriter.write(record);
        } catch (Exception e) {
            log.warn("[AuditLog] 组装审计记录失败: {}", e.getMessage());
        }
    }

    private AuditLog toRecord(AuditLogEvent event) {
        Matcher operator = OPERATOR_PATTERN.matcher(event.operator() == null ? "" : event.operator());
        if (!operator.matches()) {
            // anonymous 等无用户标识的事件不落业务审计表
            return null;
        }

        AuditLog record = new AuditLog();
        record.setOperatorId(UUID.fromString(operator.group(1)));
        record.setOperatorName(operator.group(2));
        record.setRequestIp(event.clientIp());

        String action = event.action() == null ? "" : event.action();
        int separator = action.indexOf(':');
        if (separator < 0) {
            log.warn("[AuditLog] action 缺少操作/实体分隔，跳过 action({})", action);
            return null;
        }
        record.setOperation(action.substring(0, separator));
        record.setEntityType(action.substring(separator + 1));

        Map<String, Object> params = parseParams(event.params());
        record.setEntityId(firstUuid(params.get("params")));
        record.setChanges(params);
        return record;
    }

    /** 入参 JSON 数组包装为 changes；无入参时存空对象 */
    private Map<String, Object> parseParams(String params) {
        if (params == null || params.isBlank()) {
            return Map.of();
        }
        JsonNode node = JsonUtils.toJSON(params);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("params", JsonUtils.convert(node, Object.class));
        return changes;
    }

    /** 取入参数组中首个 UUID 形态元素（对应原切面「第一个 UUID 参数」语义） */
    private UUID firstUuid(Object params) {
        if (!(params instanceof Iterable<?> elements)) {
            return null;
        }
        for (Object element : elements) {
            if (element instanceof String value) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // 非 UUID 的字符串入参继续向后查找
                }
            }
        }
        return null;
    }
}
