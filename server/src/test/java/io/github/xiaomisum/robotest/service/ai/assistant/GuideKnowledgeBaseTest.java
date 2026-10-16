package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台使用指引知识库单测（详设 4.1 search_knowledge 静态段）：真实类路径资源加载、
 * 主题词包含匹配与空主题保护（C8）。
 */
@ExtendWith(MockitoExtension.class)
class GuideKnowledgeBaseTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WS_ID = UUID.randomUUID();

    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;

    @Test
    void load_readsClasspathGuidesAndMatchesTopic() {
        GuideKnowledgeBase base = new GuideKnowledgeBase();
        ReflectionTestUtils.setField(base, "workspaceUserMapper", workspaceUserMapper);
        ReflectionTestUtils.setField(base, "sysRoleMapper", sysRoleMapper);
        base.load();

        // 8 个静态指引片段（bug-create / bug-flow / case-mindmap / plan-create /
        // plan-execute / review-create / review-participate / workspace-member）
        assertTrue(base.size() >= 8);
        List<GuideKnowledgeBase.GuideFragment> hits = base.search(USER_ID, WS_ID, "review");
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).route().startsWith("/"));
        assertFalse(hits.get(0).content().isBlank());
    }

    @Test
    void blankTopic_returnsEmptyWithoutTouchingMappers() {
        GuideKnowledgeBase base = new GuideKnowledgeBase();
        base.load();

        assertTrue(base.search(USER_ID, WS_ID, "  ").isEmpty());
        assertTrue(base.search(USER_ID, WS_ID, null).isEmpty());
    }
}
