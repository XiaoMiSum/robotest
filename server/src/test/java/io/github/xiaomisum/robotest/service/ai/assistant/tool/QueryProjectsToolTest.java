package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** 项目查询工具单测：活跃空间边界、名称关键词过滤与结果封顶（详设 4.1）（C8）。 */
@ExtendWith(MockitoExtension.class)
class QueryProjectsToolTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private QueryProjectsTool tool;

    private final AssistantToolContext context =
            new AssistantToolContext(UUID.randomUUID(), WORKSPACE_ID, List.of());

    @Test
    void keyword_filters_case_insensitively() {
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(
                project("电商商城"), project("门店后台")));
        String result = tool.execute(context, Map.of("keyword", "商城"));
        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(1, parsed.get("count"));
        Map<?, ?> first = (Map<?, ?>) ((List<?>) parsed.get("projects")).get(0);
        assertEquals("电商商城", first.get("name"));
    }

    @Test
    void empty_keyword_returns_all_capped_at_50() {
        List<Project> projects = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            projects.add(project("项目" + i));
        }
        when(projectMapper.listByWorkspaceId(WORKSPACE_ID)).thenReturn(projects);
        String result = tool.execute(context, Map.of());
        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(50, parsed.get("count"));
        assertTrue(parsed.toString().contains("项目0"));
    }

    private static Project project(String name) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(name);
        return project;
    }
}
