package io.github.xiaomisum.robotest.service.ai;


import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import io.github.xiaomisum.robotest.model.entity.ai.CaseEmbedding;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.ai.CaseEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.EmbedResult;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedAiConfig;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchService;
import io.github.xiaomisum.robotest.service.ai.vector.AiVectorSearchServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiVectorSearchServiceImplTest {

    private static final String MODEL = "text-embedding-3-small";
    private static final int DIM = 1024;
    private static final ResolvedAiConfig CONFIG =
            new ResolvedAiConfig("openai", "https://api.example.com/v1", "sk-test", MODEL, DIM, Map.of());

    @Mock
    private OpenAiCompatProvider openAiCompatProvider;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Mock
    private CaseEmbeddingMapper caseEmbeddingMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;

    @InjectMocks
    private AiVectorSearchServiceImpl service;

    private void configure() {
        when(aiConfigService.getResolvedConfig()).thenReturn(CONFIG);
    }

    @Test
    void buildBugSourceText_joinsTitleAndReproSteps() {
        Bug bug = new Bug();
        bug.setTitle("登录超时");
        bug.setReproSteps("1. 打开登录页\n2. 点击登录");
        assertEquals("登录超时\n1. 打开登录页\n2. 点击登录", service.buildBugSourceText(bug));
    }

    @Test
    void buildBugSourceText_truncatesReproStepsTo2000() {
        Bug bug = new Bug();
        bug.setTitle("标题");
        bug.setReproSteps("长".repeat(3000));
        String text = service.buildBugSourceText(bug);
        assertEquals("标题\n" + "长".repeat(2000), text);
    }

    @Test
    void buildSourceHash_changesWithModelOrText() {
        String a = service.buildSourceHash("m1", "文本");
        String b = service.buildSourceHash("m1", "文本");
        String c = service.buildSourceHash("m2", "文本");
        String d = service.buildSourceHash("m1", "文本2");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }

    @Test
    void vectorToText_producesVectorLiteral() {
        assertEquals("[0.0,1.0,-2.5]", service.vectorToText(new float[]{0f, 1f, -2.5f}));
    }

    @Test
    void buildCaseIndexTexts_includesAncestorsAndChildren() {
        UUID doc = UUID.randomUUID();
        UUID module = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        TestCaseNode parentNode = node(parent, "登录模块", Constants.NodeType.NORMAL, null, 0);
        TestCaseNode caseNode = node(caseId, "登录超时用例", Constants.NodeType.CASE, parent, 1);
        TestCaseNode step = node(UUID.randomUUID(), "点击登录", Constants.NodeType.STEP, caseId, 0);
        TestCaseNode expected = node(UUID.randomUUID(), "登录成功", Constants.NodeType.EXPECTED, caseId, 1);

        Map<UUID, String> texts = service.buildCaseIndexTexts("登录用例集",
                List.of(parentNode, caseNode, step, expected));

        String text = texts.get(caseId);
        assertTrue(text.contains("文档：登录用例集"));
        assertTrue(text.contains("路径：登录模块"));
        assertTrue(text.contains("用例标题：登录超时用例"));
        assertTrue(text.contains("步骤：点击登录"));
        assertTrue(text.contains("预期结果：登录成功"));
    }

    @Test
    void indexBug_embedsAndUpsertsWhenNoExistingVector() {
        configure();
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(UUID.randomUUID());
        bug.setTitle("登录超时");
        bug.setReproSteps("点击登录无响应");
        String text = service.buildBugSourceText(bug);
        when(bugEmbeddingMapper.findByBugId(bug.getId())).thenReturn(null);
        when(openAiCompatProvider.embed(eq(CONFIG), eq(List.of(text))))
                .thenReturn(new EmbedResult(List.of(new float[DIM]), 5));

        boolean embedded = service.indexBug(bug);

        assertTrue(embedded);
        verify(bugEmbeddingMapper).upsert(org.mockito.ArgumentMatchers.argThat(e ->
                e instanceof BugEmbedding
                        && bug.getId().equals(((BugEmbedding) e).getBugId())
                        && bug.getProjectId().equals(((BugEmbedding) e).getProjectId())
                        && MODEL.equals(((BugEmbedding) e).getModel())
                        && ((BugEmbedding) e).getSourceHash() != null
                        && ((BugEmbedding) e).getEmbedding() != null));
    }

    @Test
    void indexBug_skipsWhenHashUnchanged() {
        configure();
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(UUID.randomUUID());
        bug.setTitle("登录超时");
        String text = service.buildBugSourceText(bug);
        BugEmbedding existing = new BugEmbedding();
        existing.setBugId(bug.getId());
        existing.setModel(MODEL);
        existing.setSourceHash(service.buildSourceHash(MODEL, text));
        when(bugEmbeddingMapper.findByBugId(bug.getId())).thenReturn(existing);

        boolean embedded = service.indexBug(bug);

        assertFalse(embedded);
        verify(openAiCompatProvider, never()).embed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(bugEmbeddingMapper, never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void indexBug_returnsFalseWhenEmbeddingUnconfigured() {
        when(aiConfigService.getResolvedConfig()).thenReturn(null);
        Bug bug = new Bug();
        bug.setId(UUID.randomUUID());
        bug.setProjectId(UUID.randomUUID());
        bug.setTitle("标题");
        assertFalse(service.indexBug(bug));
    }

    @Test
    void indexCase_resolvesProjectAndUpserts() {
        configure();
        UUID doc = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        TestCaseModule module = new TestCaseModule();
        module.setId(doc);
        module.setProjectId(project);
        module.setType(Constants.ModuleType.DOCUMENT);
        module.setName("登录用例集");
        TestCaseNode caseNode = node(caseId, "登录超时用例", Constants.NodeType.CASE, null, 0);
        caseNode.setDocumentId(doc);
        when(testCaseModuleMapper.selectById(doc)).thenReturn(module);
        when(testCaseNodeMapper.listByDocumentId(doc)).thenReturn(List.of(caseNode));
        when(caseEmbeddingMapper.findByNodeId(caseId)).thenReturn(null);
        when(openAiCompatProvider.embed(eq(CONFIG), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new EmbedResult(List.of(new float[DIM]), 5));

        boolean embedded = service.indexCase(caseNode);

        assertTrue(embedded);
        verify(caseEmbeddingMapper).upsert(org.mockito.ArgumentMatchers.argThat(e ->
                e instanceof CaseEmbedding
                        && caseId.equals(((CaseEmbedding) e).getNodeId())
                        && project.equals(((CaseEmbedding) e).getProjectId())));
    }

    @Test
    void searchSimilarBugs_filtersBelowThreshold() {
        configure();
        UUID project = UUID.randomUUID();
        when(openAiCompatProvider.embed(eq(CONFIG), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new EmbedResult(List.of(new float[DIM]), 5));
        BugEmbeddingMapper.SearchRow hit = new BugEmbeddingMapper.SearchRow();
        hit.setBugId(UUID.randomUUID().toString());
        hit.setTitle("登录超时");
        hit.setStatus(Constants.BugStatus.ACTIVE);
        hit.setSimilarity(0.87);
        BugEmbeddingMapper.SearchRow low = new BugEmbeddingMapper.SearchRow();
        low.setBugId(UUID.randomUUID().toString());
        low.setTitle("无关");
        low.setSimilarity(0.5);
        when(bugEmbeddingMapper.searchTopK(anyString(), anyString(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(hit, low));

        List<AiVectorSearchService.BugDedupHit> results =
                service.searchSimilarBugs(project, "登录超时", null, null, 10, 0.6);

        assertEquals(1, results.size());
        assertEquals(hit.getBugId(), results.get(0).bugId().toString());
        assertEquals(0.87, results.get(0).similarity(), 1e-9);
    }

    @Test
    void searchSimilarBugs_returnsEmptyWhenUnconfigured() {
        when(aiConfigService.getResolvedConfig()).thenReturn(null);
        assertTrue(service.searchSimilarBugs(UUID.randomUUID(), "标题", null, null, 10, 0.6).isEmpty());
    }

    @Test
    void deleteBugIndex_logicallyDeletes() {
        UUID bugId = UUID.randomUUID();
        service.deleteBugIndex(bugId);
        verify(bugEmbeddingMapper).logicalDeleteByBugId(bugId);
    }

    private TestCaseNode node(UUID id, String title, String type, UUID parentId, int sort) {
        TestCaseNode n = new TestCaseNode();
        n.setId(id);
        n.setTitle(title);
        n.setType(type);
        n.setParentId(parentId);
        n.setSortOrder(sort);
        return n;
    }
}
