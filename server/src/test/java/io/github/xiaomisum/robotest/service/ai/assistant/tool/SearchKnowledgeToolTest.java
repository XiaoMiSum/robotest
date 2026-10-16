package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantPromptSupport;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.ai.assistant.GuideKnowledgeBase;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 知识检索工具单测（详设 4.1 search_knowledge）：指引段 + RAG 段二合一、限权与降级分支（C8）。 */
@ExtendWith(MockitoExtension.class)
class SearchKnowledgeToolTest {

    private static final UUID WS_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    @Mock
    private GuideKnowledgeBase guideKnowledgeBase;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private AssistantRefChecker refChecker;
    @Mock
    private AssistantPromptSupport promptSupport;

    @InjectMocks
    private SearchKnowledgeTool tool;

    private final AssistantToolContext context = new AssistantToolContext(USER_ID, WS_ID, List.of());

    @Test
    void mergesGuideFragmentsAndRagHits() {
        when(guideKnowledgeBase.search(USER_ID, WS_ID, "如何发起评审")).thenReturn(List.of(
                new GuideKnowledgeBase.GuideFragment(List.of("发起评审"), "/workspace/projects/reviews",
                        List.of(), "评审创建步骤…")));
        when(promptSupport.workspaceProjectIds(WS_ID)).thenReturn(List.of(PROJECT_ID));
        when(vectorSearchService.search(eq("如何发起评审"), eq(List.of(PROJECT_ID)), isNull(),
                anyInt(), eq(USER_ID))).thenReturn(List.of(hit("requirement", "REQ-001 登录需求")));
        when(refChecker.titleOf("requirement", ENTITY_ID)).thenReturn("REQ-001 登录需求");

        Map<String, Object> parsed = JsonUtils.parseObject(
                tool.execute(context, Map.of("query", "如何发起评审")), Map.class);

        assertEquals(1, ((List<?>) parsed.get("fragments")).size());
        assertEquals(1, ((List<?>) parsed.get("hits")).size());
        Map<?, ?> hit = (Map<?, ?>) ((List<?>) parsed.get("hits")).get(0);
        assertEquals("REQ-001 登录需求", hit.get("title"));
        assertEquals("REQ-001 登录需求", hit.get("quote"));
    }

    @Test
    void vectorUnavailable_keepsGuideAndReportsRagError() {
        when(guideKnowledgeBase.search(USER_ID, WS_ID, "如何创建计划")).thenReturn(List.of());
        when(promptSupport.workspaceProjectIds(WS_ID)).thenReturn(List.of(PROJECT_ID));
        when(vectorSearchService.search(anyString(), anyList(), isNull(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE));

        Map<String, Object> parsed = JsonUtils.parseObject(
                tool.execute(context, Map.of("query", "如何创建计划")), Map.class);

        assertTrue(((List<?>) parsed.get("fragments")).isEmpty());
        assertTrue(((List<?>) parsed.get("hits")).isEmpty());
        // 向量不可用降级（详设 4.1）：静态指引仍可应答，错误文本回填模型据实说明
        assertTrue(String.valueOf(parsed.get("ragError")).contains(
                String.valueOf(ErrorCodeConstants.ASSISTANT_RAG_UNAVAILABLE.code())));
    }

    @Test
    void projectIdScopesSearchAndValidatesRef() {
        when(vectorSearchService.search(anyString(), eq(List.of(PROJECT_ID)), isNull(), anyInt(), any()))
                .thenReturn(List.of());
        when(guideKnowledgeBase.search(any(), any(), anyString())).thenReturn(List.of());

        tool.execute(context, Map.of("query", "q", "projectId", PROJECT_ID.toString()));

        verify(refChecker).requireActiveWorkspaceProject(WS_ID, PROJECT_ID);
        verify(promptSupport, never()).workspaceProjectIds(any());
    }

    @Test
    void blankQuery_rejected() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(context, Map.of("query", " ")));
    }

    @SuppressWarnings("unchecked")
    private static AiVectorSearchHitRespDTO hit(String type, String content) {
        AiVectorSearchHitRespDTO dto = new AiVectorSearchHitRespDTO();
        dto.setEntityType(type);
        dto.setEntityId(ENTITY_ID);
        dto.setContent(content);
        return dto;
    }
}
