package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 助手解析提示词的上下文装配（详设 4.1 上下文组装 / 4.4 会话私有项目）：历史取近 20 条按时间
 * 正序还原（含 role=tool 结果）；页面引用附件优先回落会话快照；项目 / 候选清单不再静态注入，
 * 一律由模型经查询工具按需获取。
 */
@Component
public class AssistantPromptSupport {

    /** 多轮上下文条数上限 N（详设 4.4） */
    static final int HISTORY_LIMIT = 20;

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private ProjectMapper projectMapper;

    /** 近 N 条消息剔除当前助手占位后按时间正序返回（循环的 history 输入） */
    public List<AiAssistantMessage> history(UUID conversationId, UUID excludeMessageId) {
        return messageMapper.listRecent(conversationId, HISTORY_LIMIT).stream()
                .filter(item -> !item.getId().equals(excludeMessageId))
                .sorted(Comparator.comparing(AiAssistantMessage::getCreatedAt))
                .toList();
    }

    /** 历史渲染：角色 + 正文，正文为空的消息（错误态等）跳过 */
    public static String historyText(List<AiAssistantMessage> history) {
        StringBuilder text = new StringBuilder();
        for (AiAssistantMessage item : history) {
            if (item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(roleName(item.getRole())).append(": ").append(item.getContent().trim());
        }
        return text.toString();
    }

    /**
     * 上下文变量（详设 4.1 / 4.4）：会话私有项目状态 + 页面引用（附件缺失回落会话创建时的
     * context_snapshot，总册 2.9）；活跃空间项目清单不再静态注入，由 query_projects 按需查询。
     */
    public String contextText(List<Map<String, Object>> attachments, AiAssistantConversation conversation) {
        Map<String, Object> snapshot = conversation == null ? null : conversation.getContextSnapshot();
        StringBuilder text = new StringBuilder();
        text.append("会话项目状态（projectId | 名称）：\n");
        Object projectId = snapshot == null ? null : snapshot.get("projectId");
        if (projectId == null) {
            text.append("- （未定）\n");
        } else {
            text.append("- ").append(projectId).append(" | ")
                    .append(snapshot.getOrDefault("projectName", "")).append('\n');
        }
        Map<String, Object> page = pageContext(attachments, conversation);
        text.append("页面上下文（类型 | 名称 | 标识）：\n");
        if (page.isEmpty()) {
            text.append("- （无）");
        } else {
            text.append("- ").append(str(page.get("entityType"))).append(" | ")
                    .append(str(page.get("entityTitle"))).append(" | ").append(str(page.get("entityId")));
        }
        return text.toString();
    }

    /** 活跃空间项目集（RAG 限权检索入参，详设 4.4 读侧） */
    public List<UUID> workspaceProjectIds(UUID workspaceId) {
        return projectMapper.listByWorkspaceId(workspaceId).stream().map(Project::getId).toList();
    }

    public String projectName(UUID projectId) {
        Project project = projectId == null ? null : projectMapper.selectById(projectId);
        return project == null ? "" : project.getName();
    }

    /** 页面引用：优先本条消息附件，缺省回落会话创建快照（不参与权限判定，仅作解析上下文） */
    private static Map<String, Object> pageContext(List<Map<String, Object>> attachments,
            AiAssistantConversation conversation) {
        if (attachments != null && !attachments.isEmpty()) {
            return attachments.get(0);
        }
        Map<String, Object> snapshot = conversation == null ? null : conversation.getContextSnapshot();
        Object context = snapshot == null ? null : snapshot.get("context");
        if (context instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            if (normalized.get("entityId") != null) {
                return normalized;
            }
        }
        return Map.of();
    }

    private static String roleName(String role) {
        if (Constants.AiAssistantMessageRole.USER.equals(role)) {
            return "用户";
        }
        if (Constants.AiAssistantMessageRole.ASSISTANT.equals(role)) {
            return "助手";
        }
        if (Constants.AiAssistantMessageRole.TOOL.equals(role)) {
            return "工具结果";
        }
        return role == null ? "" : role;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
