package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 提示词上下文装配单测（详设 4.1 上下文组装 / 4.4 会话私有项目）：历史正序与占位剔除、
 * 会话项目状态与页面引用回落、限权项目集（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantPromptSupportTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID EXCLUDED_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private AssistantPromptSupport support;

    // ---------- 历史 ----------

    @Test
    void history_excludesPlaceholderAndSortsAscending() {
        AiAssistantMessage user = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.USER,
                "帮我分析登录覆盖", LocalDateTime.now().minusMinutes(2));
        AiAssistantMessage answered = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                "已解析", LocalDateTime.now().minusMinutes(1));
        AiAssistantMessage placeholder = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                null, LocalDateTime.now());
        placeholder.setId(EXCLUDED_ID);
        // listRecent 返回倒序，且含当前 assistant 占位（详设 4.4）
        when(messageMapper.listRecent(CONVERSATION_ID, 20))
                .thenReturn(List.of(placeholder, answered, user));

        List<AiAssistantMessage> history = support.history(CONVERSATION_ID, EXCLUDED_ID);

        assertEquals(List.of(user, answered), history);
    }

    @Test
    void historyText_formatsRolesIncludingToolAndSkipsBlankContent() {
        AiAssistantMessage user = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.USER,
                "  帮我新建用例 ", LocalDateTime.now());
        AiAssistantMessage failed = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                "   ", LocalDateTime.now());
        AiAssistantMessage tool = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.TOOL,
                "{\"count\":2}", LocalDateTime.now());
        AiAssistantMessage assistant = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                "已解析为 3 条用例", LocalDateTime.now());

        String text = AssistantPromptSupport.historyText(List.of(user, failed, tool, assistant));

        // 工具结果计入多轮上下文（详设 4.1 / 4.4），空白正文跳过
        assertEquals("用户: 帮我新建用例\n工具结果: {\"count\":2}\n助手: 已解析为 3 条用例", text);
    }

    // ---------- 上下文 ----------

    @Test
    void contextText_rendersProjectStateAndAttachmentPage() {
        AiAssistantConversation conversation = conversation(Map.of(
                "projectId", PROJECT_ID.toString(), "projectName", "商城项目",
                "context", Map.of("entityType", "mindmap_document", "entityId", DOCUMENT_ID.toString())));
        Map<String, Object> attachment = Map.of(
                "entityType", "test_plan", "entityId", UUID.randomUUID().toString(), "entityTitle", "回归计划");

        String text = support.contextText(List.of(attachment), conversation);

        assertTrue(text.contains(PROJECT_ID.toString()));
        assertTrue(text.contains("商城项目"));
        // 页面上下文附件优先于会话快照（4.1 上下文组装）
        assertTrue(text.contains("回归计划"));
    }

    @Test
    void contextText_fallsBackToSnapshotContext_whenAttachmentsAbsent() {
        AiAssistantConversation conversation = conversation(Map.of(
                "projectId", PROJECT_ID.toString(),
                "context", Map.of("entityType", "test_plan", "entityId", UUID.randomUUID().toString(),
                        "entityTitle", "快照计划")));

        String text = support.contextText(null, conversation);

        assertTrue(text.contains("快照计划"));
    }

    @Test
    void contextText_marksUndecided_whenSnapshotMissing() {
        String text = support.contextText(List.of(), new AiAssistantConversation());

        assertTrue(text.contains("（未定）"));
        assertTrue(text.contains("（无）"));
    }

    // ---------- 项目辅助 ----------

    @Test
    void workspaceProjectIds_returnsAllWorkspaceProjects() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(project(PROJECT_ID, "A"), project(UUID.randomUUID(), "B")));

        assertEquals(2, support.workspaceProjectIds(WORKSPACE_ID).size());
    }

    @Test
    void projectName_returnsNameOrEmpty() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "商城项目"));

        assertEquals("商城项目", support.projectName(PROJECT_ID));
        assertEquals("", support.projectName(null));
    }

    // ---------- 辅助 ----------

    private static AiAssistantMessage message(UUID conversationId, String role, String content,
            LocalDateTime createdAt) {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        return message;
    }

    private static AiAssistantConversation conversation(Map<String, Object> snapshot) {
        AiAssistantConversation conversation = new AiAssistantConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setWorkspaceId(WORKSPACE_ID);
        conversation.setContextSnapshot(snapshot);
        return conversation;
    }

    private static Project project(UUID id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }
}
