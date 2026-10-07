package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 助手解析提示词的上下文装配（详设 4.4 多轮上下文与限权候选）：历史取近 20 条按时间正序还原，
 * 候选清单一律以活跃工作空间项目集为边界，附件指向脑图文档时用例候选按当前页面文档聚焦。
 */
@Component
public class AssistantPromptSupport {

    /** 多轮上下文条数上限 N（详设 4.4） */
    static final int HISTORY_LIMIT = 20;

    private static final int DOC_IDS_MAX = 200;
    private static final int CASE_OPTIONS_MAX = 200;
    private static final int OPTION_PER_PROJECT = 20;
    private static final int OPTION_MAX = 100;

    @Resource
    private AiAssistantMessageMapper messageMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestPlanMapper testPlanMapper;

    /** 近 N 条消息剔除当前助手占位后按时间正序返回（判别段的 history 输入） */
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

    /** 上下文变量：活跃空间项目清单 + 页面引用（附件缺失回落会话创建时的 context_snapshot，总册 2.9） */
    public String contextText(UUID workspaceId, List<Map<String, Object>> attachments,
            AiAssistantConversation conversation) {
        StringBuilder text = new StringBuilder();
        List<Project> projects = projectMapper.listByWorkspaceId(workspaceId);
        text.append("活跃工作空间项目（项目ID | 名称）：\n");
        for (Project project : projects) {
            text.append("- ").append(project.getId()).append(" | ").append(project.getName()).append('\n');
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

    /** 候选用例清单：附件聚焦脑图文档时只取该文档用例，否则取活跃空间项目内用例（分页封顶） */
    public String caseOptions(UUID workspaceId, List<Map<String, Object>> attachments) {
        List<UUID> documentIds = new ArrayList<>();
        Map<UUID, String> projectByDocument = new LinkedHashMap<>();
        UUID focusDocumentId = focusDocumentId(attachments);
        if (focusDocumentId != null) {
            TestCaseDocument document = testCaseDocumentMapper.selectById(focusDocumentId);
            if (document != null) {
                documentIds.add(document.getId());
                projectByDocument.put(document.getId(), projectName(document.getProjectId()));
            }
        } else {
            outer:
            for (Project project : projectMapper.listByWorkspaceId(workspaceId)) {
                for (TestCaseDocument document : testCaseDocumentMapper.listByProjectId(project.getId())) {
                    if (documentIds.size() >= DOC_IDS_MAX) {
                        break outer;
                    }
                    documentIds.add(document.getId());
                    projectByDocument.put(document.getId(), project.getName());
                }
            }
        }
        if (documentIds.isEmpty()) {
            return "";
        }
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(CASE_OPTIONS_MAX);
        PageResult<TestCaseNode> cases = testCaseNodeMapper.findCasePage(page, documentIds, null, null);
        StringBuilder text = new StringBuilder();
        for (TestCaseNode item : cases.getList()) {
            text.append("- ").append(item.getId()).append(" | ").append(item.getTitle())
                    .append(" | ").append(projectByDocument.getOrDefault(item.getDocumentId(), "")).append('\n');
        }
        return text.toString();
    }

    /** 候选评审清单：按项目取近期评审，总量封顶 */
    public String reviewOptions(UUID workspaceId) {
        StringBuilder text = new StringBuilder();
        int lines = 0;
        for (Project project : projectMapper.listByWorkspaceId(workspaceId)) {
            for (TestReview review : testReviewMapper.findRecentReviews(project.getId(), OPTION_PER_PROJECT)) {
                if (lines >= OPTION_MAX) {
                    return text.toString();
                }
                text.append("- ").append(review.getId()).append(" | ").append(review.getTitle())
                        .append(" | ").append(project.getName()).append('\n');
                lines++;
            }
        }
        return text.toString();
    }

    /** 候选计划清单：按项目取近期计划，总量封顶 */
    public String planOptions(UUID workspaceId) {
        StringBuilder text = new StringBuilder();
        int lines = 0;
        for (Project project : projectMapper.listByWorkspaceId(workspaceId)) {
            for (TestPlan plan : testPlanMapper.findRecentPlans(project.getId(), OPTION_PER_PROJECT)) {
                if (lines >= OPTION_MAX) {
                    return text.toString();
                }
                text.append("- ").append(plan.getId()).append(" | ").append(plan.getName())
                        .append(" | ").append(project.getName()).append('\n');
                lines++;
            }
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

    private static UUID focusDocumentId(List<Map<String, Object>> attachments) {
        if (attachments == null) {
            return null;
        }
        for (Map<String, Object> attachment : attachments) {
            if (attachment == null
                    || !Constants.TraceNodeType.MINDMAP_DOCUMENT.equals(str(attachment.get("entityType")))) {
                continue;
            }
            try {
                return UUID.fromString(str(attachment.get("entityId")));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String roleName(String role) {
        if (Constants.AiAssistantMessageRole.USER.equals(role)) {
            return "用户";
        }
        if (Constants.AiAssistantMessageRole.ASSISTANT.equals(role)) {
            return "助手";
        }
        return role == null ? "" : role;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
