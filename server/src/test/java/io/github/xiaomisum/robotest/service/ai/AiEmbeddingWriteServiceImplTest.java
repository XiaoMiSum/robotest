package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.ai.CaseEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEmbeddingWriteServiceImplTest {

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
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Mock
    private CaseEmbeddingMapper caseEmbeddingMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AiEmbeddingWriteServiceImpl service;

    private Bug bug() {
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(UUID.randomUUID());
        bug.setTitle("登录超时");
        bug.setStatus(Constants.BugStatus.ACTIVE);
        return bug;
    }

    @Test
    void handleBugChanged_deletesIndexWhenClosed() {
        Bug bug = bug();
        bug.setStatus(Constants.BugStatus.CLOSED);
        service.handleBugChanged(bug);
        verify(vectorSearchService).deleteBugIndex(bug.getId());
        verify(vectorSearchService, never()).indexBug(any());
    }

    @Test
    void handleBugChanged_skipsDuringRebuild() {
        when(aiAnalysisTaskMapper.hasInProgressRebuild()).thenReturn(true);
        service.handleBugChanged(bug());
        verify(vectorSearchService, never()).indexBug(any());
        verify(vectorSearchService, never()).deleteBugIndex(any());
    }

    @Test
    void handleBugChanged_indexesActiveBug() {
        Bug bug = bug();
        service.handleBugChanged(bug);
        verify(vectorSearchService).indexBug(bug);
    }

    @Test
    void handleBugChanged_ignoresNullId() {
        Bug bug = bug();
        bug.setId(null);
        service.handleBugChanged(bug);
        verify(vectorSearchService, never()).indexBug(any());
    }

    @Test
    void handleBugDeleted_deletesIndex() {
        UUID bugId = UUID.randomUUID();
        service.handleBugDeleted(bugId);
        verify(vectorSearchService).deleteBugIndex(bugId);
    }

    @Test
    void handleCaseDeleted_deletesIndex() {
        UUID nodeId = UUID.randomUUID();
        service.handleCaseDeleted(nodeId);
        verify(vectorSearchService).deleteCaseIndex(nodeId);
    }

    @Test
    void compensate_skipsWhenRebuildInProgress() {
        when(aiAnalysisTaskMapper.hasInProgressRebuild()).thenReturn(true);
        service.compensate();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void compensate_skipsWhenLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("ai:embedding:compensate:lock"), anyString(), any(Duration.class)))
                .thenReturn(false);
        service.compensate();
        verify(projectMapper, never()).listAll();
    }

    @Test
    void compensate_embedsMissingBugsAndReleasesLock() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        Bug bug = bug();
        when(aiAnalysisTaskMapper.hasInProgressRebuild()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<String> instanceCaptor = ArgumentCaptor.forClass(String.class);
        when(valueOps.setIfAbsent(eq("ai:embedding:compensate:lock"), instanceCaptor.capture(), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.get("ai:embedding:compensate:lock")).thenAnswer(inv -> instanceCaptor.getValue());
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(vectorSearchService.embeddingModel()).thenReturn("text-embedding-3-small");
        when(bugMapper.findOpenBugsByProjectId(project.getId())).thenReturn(List.of(bug));
        when(bugEmbeddingMapper.findHashesByBugIds(any())).thenReturn(List.of());
        when(vectorSearchService.buildBugSourceText(bug)).thenReturn("标题\n步骤");
        when(vectorSearchService.buildSourceHash(eq("text-embedding-3-small"), eq("标题\n步骤")))
                .thenReturn("expected-hash");
        when(vectorSearchService.indexBug(bug)).thenReturn(true);
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());

        service.compensate();

        verify(vectorSearchService).indexBug(bug);
        verify(redisTemplate).delete("ai:embedding:compensate:lock");
    }

    @Test
    void compensate_skipsStaleVectors() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        Bug bug = bug();
        when(aiAnalysisTaskMapper.hasInProgressRebuild()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<String> instanceCaptor = ArgumentCaptor.forClass(String.class);
        when(valueOps.setIfAbsent(eq("ai:embedding:compensate:lock"), instanceCaptor.capture(), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.get("ai:embedding:compensate:lock")).thenAnswer(inv -> instanceCaptor.getValue());
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(vectorSearchService.embeddingModel()).thenReturn("text-embedding-3-small");
        when(bugMapper.findOpenBugsByProjectId(project.getId())).thenReturn(List.of(bug));
        BugEmbedding stored = new BugEmbedding();
        stored.setBugId(bug.getId());
        stored.setSourceHash("expected-hash");
        when(bugEmbeddingMapper.findHashesByBugIds(any())).thenReturn(List.of(stored));
        when(vectorSearchService.buildBugSourceText(bug)).thenReturn("标题\n步骤");
        when(vectorSearchService.buildSourceHash(eq("text-embedding-3-small"), eq("标题\n步骤")))
                .thenReturn("expected-hash");
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());

        service.compensate();

        verify(vectorSearchService, never()).indexBug(bug);
    }

    @Test
    void compensate_doesNotReleaseForeignLock() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        when(aiAnalysisTaskMapper.hasInProgressRebuild()).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("ai:embedding:compensate:lock"), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(valueOps.get("ai:embedding:compensate:lock")).thenReturn("another-instance");
        when(projectMapper.listAll()).thenReturn(List.of(project));
        when(testCaseModuleMapper.findDocumentModulesByProjectId(project.getId())).thenReturn(List.of());

        service.compensate();

        verify(redisTemplate, never()).delete("ai:embedding:compensate:lock");
    }
}
