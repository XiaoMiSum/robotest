package io.github.xiaomisum.robotest.service.ai.vector;


import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEmbeddingRebuildTaskHandlerTest {

    @Mock
    private AiVectorSearchService vectorSearchService;
    @Mock
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AiEmbeddingRebuildTaskHandler handler;

    private AiAnalysisTask task() {
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(UUID.randomUUID());
        return task;
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        return project;
    }

    @Test
    void type_returnsEmbeddingRebuild() {
        assertEquals(Constants.AiTaskType.EMBEDDING_REBUILD, handler.type());
    }

    @Test
    void execute_failsWhenEmbeddingUnconfigured() {
        when(vectorSearchService.configuredEmbeddingDimension()).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> handler.execute(task()));
    }

    @Test
    void execute_rebuildsAndAlignsDimensionOnMismatch() {
        AiAnalysisTask task = task();
        Project project = project();
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(project.getId());
        bug.setTitle("登录超时");
        when(vectorSearchService.configuredEmbeddingDimension()).thenReturn(1024);
        when(vectorSearchService.embeddingModel()).thenReturn("m");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(516);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(bugMapper.findOpenBugsByProjectId(project.getId())).thenReturn(List.of(bug));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());
        when(vectorSearchService.buildBugSourceText(bug)).thenReturn("登录超时\n步骤");
        when(aiAnalysisTaskMapper.updateProgressIfRunning(eq(task.getId()), anyInt(), anyString())).thenReturn(1);
        when(vectorSearchService.embedBatch(List.of("登录超时\n步骤"))).thenReturn(List.of(new float[1024]));
        when(vectorSearchService.buildSourceHash("m", "登录超时\n步骤")).thenReturn("hash");

        Map<String, Object> result = handler.execute(task);

        verify(jdbcTemplate).execute("DROP INDEX IF EXISTS idx_ai_bug_embedding_hnsw");
        verify(jdbcTemplate).execute("DROP INDEX IF EXISTS idx_ai_case_embedding_hnsw");
        verify(jdbcTemplate).execute("ALTER TABLE ai_bug_embedding ALTER COLUMN embedding TYPE vector(1024)");
        verify(jdbcTemplate).execute("ALTER TABLE ai_case_embedding ALTER COLUMN embedding TYPE vector(1024)");
        verify(vectorSearchService).upsertBug(eq(bug.getId()), eq(project.getId()), any(float[].class), eq("hash"));
        assertEquals(1, result.get("bugCount"));
        assertEquals(1, result.get("embeddedCount"));
        assertEquals(0, result.get("failedCount"));
    }

    @Test
    void execute_truncatesOnlyWhenDimensionMatches() {
        AiAnalysisTask task = task();
        when(vectorSearchService.configuredEmbeddingDimension()).thenReturn(1024);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1028);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(projectMapper.listAll()).thenReturn(List.of());

        handler.execute(task);

        verify(jdbcTemplate).execute("TRUNCATE TABLE ai_bug_embedding, ai_case_embedding");
        verify(jdbcTemplate).execute("CREATE INDEX IF NOT EXISTS idx_ai_bug_embedding_hnsw ON ai_bug_embedding USING hnsw (embedding vector_cosine_ops)");
        verify(jdbcTemplate, never()).execute("ALTER TABLE ai_bug_embedding ALTER COLUMN embedding TYPE vector(1024)");
    }

    @Test
    void execute_abortsWhenTaskCancelled() {
        AiAnalysisTask task = task();
        Project project = project();
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(project.getId());
        bug.setTitle("标题");
        when(vectorSearchService.configuredEmbeddingDimension()).thenReturn(1024);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(516);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(bugMapper.findOpenBugsByProjectId(project.getId())).thenReturn(List.of(bug));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());
        when(vectorSearchService.buildBugSourceText(bug)).thenReturn("标题\n步骤");
        when(aiAnalysisTaskMapper.updateProgressIfRunning(any(), anyInt(), anyString())).thenReturn(0);

        Map<String, Object> result = handler.execute(task);

        verify(vectorSearchService, never()).embedBatch(any());
        assertEquals(0, result.get("embeddedCount"));
        assertEquals(0, result.get("bugCount"));
    }

    @Test
    void execute_failsWhenFailureRateExceedsLimit() {
        AiAnalysisTask task = task();
        Project project = project();
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(project.getId());
        bug.setTitle("标题");
        when(vectorSearchService.configuredEmbeddingDimension()).thenReturn(1024);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(516);
        doNothing().when(jdbcTemplate).execute(anyString());
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(bugMapper.findOpenBugsByProjectId(project.getId())).thenReturn(List.of(bug));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());
        when(vectorSearchService.buildBugSourceText(bug)).thenReturn("标题\n步骤");
        when(aiAnalysisTaskMapper.updateProgressIfRunning(any(), anyInt(), anyString())).thenReturn(1);
        when(vectorSearchService.embedBatch(any())).thenThrow(new RuntimeException("network"));

        assertThrows(IllegalStateException.class, () -> handler.execute(task));
    }
}
