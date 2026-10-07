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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析提示词上下文装配单测（详设 4.4）：历史正序与占位剔除、页面引用回落、
 * 候选清单的附件聚焦与工作空间边界、评审 / 计划候选总量封顶（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantPromptSupportTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID EXCLUDED_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestReviewMapper testReviewMapper;
    @Mock
    private TestPlanMapper testPlanMapper;

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
    void historyText_formatsRolesAndSkipsBlankContent() {
        AiAssistantMessage user = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.USER,
                "  帮我新建用例 ", LocalDateTime.now());
        AiAssistantMessage failed = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                "   ", LocalDateTime.now());
        AiAssistantMessage assistant = message(CONVERSATION_ID, Constants.AiAssistantMessageRole.ASSISTANT,
                "已解析为 3 条用例", LocalDateTime.now());

        String text = AssistantPromptSupport.historyText(List.of(user, failed, assistant));

        assertEquals("用户: 帮我新建用例\n助手: 已解析为 3 条用例", text);
    }

    // ---------- 上下文 ----------

    @Test
    void contextText_rendersWorkspaceProjectsAndAttachmentPage() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(project(PROJECT_ID, "商城项目")));
        Map<String, Object> attachment = Map.of(
                "entityType", Constants.TraceNodeType.REQUIREMENT,
                "entityId", REQUIREMENT_ID.toString(),
                "entityTitle", "REQ-001 登录");

        String text = support.contextText(WORKSPACE_ID, List.of(attachment), new AiAssistantConversation());

        assertTrue(text.contains("活跃工作空间项目（项目ID | 名称）"));
        assertTrue(text.contains(PROJECT_ID + " | 商城项目"));
        assertTrue(text.contains("requirement | REQ-001 登录 | " + REQUIREMENT_ID));
    }

    @Test
    void contextText_fallsBackToConversationSnapshot_whenAttachmentsAbsent() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());
        AiAssistantConversation conversation = new AiAssistantConversation();
        conversation.setContextSnapshot(Map.of(
                "workspaceId", WORKSPACE_ID,
                "context", Map.of(
                        "entityType", Constants.TraceNodeType.TEST_PLAN,
                        "entityId", PLAN_ID.toString(),
                        "entityTitle", "回归计划")));

        String text = support.contextText(WORKSPACE_ID, List.of(), conversation);

        assertTrue(text.contains("test_plan | 回归计划 | " + PLAN_ID));
    }

    @Test
    void contextText_marksNoPage_whenSnapshotMissing() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

        String text = support.contextText(WORKSPACE_ID, null, new AiAssistantConversation());

        assertTrue(text.contains("- （无）"));
    }

    // ---------- 候选清单 ----------

    @Test
    void caseOptions_focusesAttachmentDocumentPage() {
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document(DOCUMENT_ID, PROJECT_ID));
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "商城项目"));
        when(testCaseNodeMapper.findCasePage(any(PageParam.class), any(), isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(caseNode()), 1L));
        Map<String, Object> attachment = Map.of(
                "entityType", Constants.TraceNodeType.MINDMAP_DOCUMENT,
                "entityId", DOCUMENT_ID.toString());

        String options = support.caseOptions(WORKSPACE_ID, List.of(attachment));

        assertEquals("- " + CASE_ID + " | 验证码正确可登录 | 商城项目\n", options);
        // 聚焦模式只取当前页面文档，不展开全空间（4.4）
        verify(testCaseDocumentMapper, never()).listByProjectId(any());

        ArgumentCaptor<PageParam> pageCaptor = ArgumentCaptor.forClass(PageParam.class);
        verify(testCaseNodeMapper).findCasePage(pageCaptor.capture(), any(), isNull(), isNull());
        assertEquals(1, pageCaptor.getValue().getPageNo());
        assertEquals(200, pageCaptor.getValue().getPageSize());
    }

    @Test
    void caseOptions_collectsWorkspaceDocuments_whenNoFocusAttachment() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(project(PROJECT_ID, "商城项目")));
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID))
                .thenReturn(List.of(document(DOCUMENT_ID, PROJECT_ID)));
        when(testCaseNodeMapper.findCasePage(any(PageParam.class), any(), isNull(), isNull()))
                .thenReturn(new PageResult<>(List.of(caseNode()), 1L));

        String options = support.caseOptions(WORKSPACE_ID, List.of());

        assertEquals("- " + CASE_ID + " | 验证码正确可登录 | 商城项目\n", options);
        verify(projectMapper, never()).selectById(any(UUID.class));
    }

    @Test
    void caseOptions_emptyWhenNoDocuments() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(project(PROJECT_ID, "商城项目")));
        when(testCaseDocumentMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());

        assertEquals("", support.caseOptions(WORKSPACE_ID, null));
        verify(testCaseNodeMapper, never()).findCasePage(any(), any(), any(), any());
    }

    @Test
    void reviewOptions_capsAtHundredLines() {
        List<Project> projects = new ArrayList<>();
        List<TestReview> reviews = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            projects.add(project(UUID.randomUUID(), "项目" + i));
        }
        for (int i = 0; i < 20; i++) {
            TestReview review = new TestReview();
            review.setId(UUID.randomUUID());
            review.setTitle("评审" + i);
            reviews.add(review);
        }
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(projects);
        when(testReviewMapper.findRecentReviews(any(UUID.class), eq(20))).thenReturn(reviews);

        String options = support.reviewOptions(WORKSPACE_ID);

        // 6 项目 × 20 = 120 行，封顶 100（详设 4.4 候选上限）
        assertEquals(100, countLines(options));
    }

    @Test
    void planOptions_rendersIdNameProject() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(project(PROJECT_ID, "商城项目")));
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setName("回归计划");
        when(testPlanMapper.findRecentPlans(PROJECT_ID, 20)).thenReturn(List.of(plan));

        String options = support.planOptions(WORKSPACE_ID);

        assertEquals("- " + PLAN_ID + " | 回归计划 | 商城项目\n", options);
    }

    // ---------- 项目集 ----------

    @Test
    void workspaceProjectIds_returnsAllWorkspaceProjects() {
        Project first = project(PROJECT_ID, "商城项目");
        Project second = project(OTHER_PROJECT_ID, "结算项目");
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(first, second));

        assertEquals(List.of(PROJECT_ID, OTHER_PROJECT_ID), support.workspaceProjectIds(WORKSPACE_ID));
    }

    @Test
    void projectName_returnsNameOrEmpty() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project(PROJECT_ID, "商城项目"));

        assertEquals("商城项目", support.projectName(PROJECT_ID));
        assertEquals("", support.projectName(null));
        assertEquals("", support.projectName(OTHER_PROJECT_ID));
    }

    // ---------- 辅助 ----------

    private static int countLines(String text) {
        return (int) text.chars().filter(ch -> ch == '\n').count();
    }

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

    private static Project project(UUID id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setWorkspaceId(WORKSPACE_ID);
        return project;
    }

    private static TestCaseDocument document(UUID id, UUID projectId) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(id);
        document.setProjectId(projectId);
        document.setName("登录脑图");
        return document;
    }

    private static TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(CASE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setTitle("验证码正确可登录");
        return node;
    }
}
