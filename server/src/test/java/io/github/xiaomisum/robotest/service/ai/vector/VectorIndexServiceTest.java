package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiVectorIndex;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiVectorIndexMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorIndexServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private AiEmbeddingGate gate;
    @Mock
    private AiEmbeddingClient embeddingClient;
    @Mock
    private AiVectorIndexMapper indexMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private BugMapper bugMapper;

    @InjectMocks
    private VectorIndexService service;

    private static AiEmbeddingConfig readyConfig() {
        AiEmbeddingConfig config = new AiEmbeddingConfig();
        config.setEmbeddingModel("text-embedding-3");
        config.setDimensions(2);
        config.setOperator("cosine");
        return config;
    }

    private static Requirement requirement(String status) {
        Requirement row = new Requirement();
        row.setId(UUID.randomUUID());
        row.setProjectId(PROJECT_ID);
        row.setTitle("登录功能");
        row.setDescription("用户可使用账号密码登录");
        row.setStatus(status);
        return row;
    }

    @Test
    void upsertRequirementSkipsWhenEmbeddingUnready() {
        when(gate.writableConfig()).thenReturn(null);

        service.upsertRequirement(UUID.randomUUID(), USER_ID);

        verifyNoInteractions(requirementMapper, embeddingClient, indexMapper);
    }

    @Test
    void upsertRequirementRemovesArchivedEntity() {
        when(gate.writableConfig()).thenReturn(readyConfig());
        Requirement archived = requirement(Constants.RequirementStatus.ARCHIVED);
        when(requirementMapper.selectById(archived.getId())).thenReturn(archived);

        service.upsertRequirement(archived.getId(), USER_ID);

        verify(indexMapper).delete(any());
        verify(embeddingClient, never()).embedBatch(any(), anyList(), any(), any(), any());
    }

    @Test
    void upsertRequirementRemovesMissingEntity() {
        when(gate.writableConfig()).thenReturn(readyConfig());
        when(requirementMapper.selectById(any())).thenReturn(null);

        service.upsertRequirement(UUID.randomUUID(), USER_ID);

        verify(indexMapper).delete(any());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void upsertRequirementEmbedsThenReplacesIndex() {
        AiEmbeddingConfig config = readyConfig();
        when(gate.writableConfig()).thenReturn(config);
        Requirement row = requirement(Constants.RequirementStatus.CONFIRMED);
        when(requirementMapper.selectById(row.getId())).thenReturn(row);
        when(embeddingClient.embedBatch(eq(config), anyList(), eq(USER_ID), isNull(), eq(PROJECT_ID)))
                .thenReturn(new AiEmbeddingClient.EmbeddingsReply(
                        List.of(List.of(1.0, 2.0), List.of(3.0, 4.0)), 7));

        service.upsertRequirement(row.getId(), USER_ID);

        ArgumentCaptor<List<AiVectorIndex>> captor = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(indexMapper, embeddingClient);
        order.verify(embeddingClient).embedBatch(eq(config), anyList(), eq(USER_ID), isNull(), eq(PROJECT_ID));
        order.verify(indexMapper).delete(any());
        order.verify(indexMapper).insertBatch(captor.capture());
        List<AiVectorIndex> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertEquals(VectorIndexService.TYPE_REQUIREMENT, rows.get(0).getEntityType());
        assertEquals(row.getId(), rows.get(0).getEntityId());
        assertEquals(PROJECT_ID, rows.get(0).getProjectId());
        assertEquals(0, rows.get(0).getChunkIndex());
        assertEquals("[1.0,2.0]", rows.get(0).getEmbedding());
        assertEquals("text-embedding-3:2:cosine", rows.get(0).getEmbeddingVersion());
        assertNotNull(rows.get(0).getId());
        assertNotNull(rows.get(0).getIndexedAt());
    }

    @Test
    void embedFailurePropagatesWithoutTouchingIndex() {
        AiEmbeddingConfig config = readyConfig();
        when(gate.writableConfig()).thenReturn(config);
        Requirement row = requirement(Constants.RequirementStatus.DRAFT);
        when(requirementMapper.selectById(row.getId())).thenReturn(row);
        when(embeddingClient.embedBatch(any(), anyList(), any(), any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.upsertRequirement(row.getId(), USER_ID));

        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), ex.getCode());
        verify(indexMapper, never()).delete(any());
        verify(indexMapper, never()).insertBatch(any());
    }

    @Test
    void upsertTestCaseAssemblesDocumentAndNodeTitles() {
        AiEmbeddingConfig config = readyConfig();
        when(gate.writableConfig()).thenReturn(config);
        UUID documentId = UUID.randomUUID();
        TestCaseDocument document = new TestCaseDocument();
        document.setId(documentId);
        document.setProjectId(PROJECT_ID);
        document.setName("登录用例");
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(document);
        TestCaseNode titled = new TestCaseNode();
        titled.setTitle("输入错误密码提示失败");
        TestCaseNode duplicatedRoot = new TestCaseNode();
        duplicatedRoot.setTitle("登录用例");
        when(testCaseNodeMapper.selectList(any())).thenReturn(List.of(titled, duplicatedRoot));
        // 文档名 + 一个非重名节点标题 → 2 个分块，响应向量须一一对应
        when(embeddingClient.embedBatch(eq(config), anyList(), eq(USER_ID), isNull(), eq(PROJECT_ID)))
                .thenReturn(new AiEmbeddingClient.EmbeddingsReply(
                        List.of(List.of(0.5, 0.5), List.of(0.6, 0.4)), 3));

        service.upsertTestCase(documentId, USER_ID);

        ArgumentCaptor<List<AiVectorIndex>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexMapper).insertBatch(captor.capture());
        List<AiVectorIndex> rows = captor.getValue();
        assertEquals(2, rows.size());
        String joined = rows.stream().map(AiVectorIndex::getContent).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joined.contains("登录用例"));
        assertTrue(joined.contains("输入错误密码提示失败"));
        // 根节点标题与文档名重复时只入一份，避免冗余语境
        assertEquals(joined.indexOf("登录用例"), joined.lastIndexOf("登录用例"));
        assertEquals(VectorIndexService.TYPE_TESTCASE, rows.get(0).getEntityType());
    }

    @Test
    void upsertBugIndexesTitleAndReproSteps() {
        AiEmbeddingConfig config = readyConfig();
        when(gate.writableConfig()).thenReturn(config);
        UUID bugId = UUID.randomUUID();
        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(PROJECT_ID);
        bug.setTitle("导出超时");
        bug.setReproSteps("点击导出后 30 秒无响应");
        when(bugMapper.selectById(bugId)).thenReturn(bug);
        // 标题行 + 正文行 → 2 个分块
        when(embeddingClient.embedBatch(eq(config), anyList(), eq(USER_ID), isNull(), eq(PROJECT_ID)))
                .thenReturn(new AiEmbeddingClient.EmbeddingsReply(
                        List.of(List.of(0.1, 0.9), List.of(0.2, 0.8)), 5));

        service.upsertBug(bugId, USER_ID);

        ArgumentCaptor<List<AiVectorIndex>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexMapper).insertBatch(captor.capture());
        List<AiVectorIndex> rows = captor.getValue();
        assertEquals(2, rows.size());
        String joined = rows.stream().map(AiVectorIndex::getContent).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joined.contains("导出超时"));
        assertTrue(joined.contains("点击导出后 30 秒无响应"));
        assertEquals(VectorIndexService.TYPE_BUG, rows.get(0).getEntityType());
    }

    @Test
    void removeRequirementBypassesEmbeddingGate() {
        service.removeRequirement(UUID.randomUUID());

        verify(indexMapper).delete(any());
        verifyNoInteractions(gate, embeddingClient);
    }

    @Test
    void contentForMissingEntityOrUnknownTypeReturnsNull() {
        when(requirementMapper.selectById(any())).thenReturn(null);
        assertNull(service.contentFor(VectorIndexService.TYPE_REQUIREMENT, UUID.randomUUID()));
        assertNull(service.contentFor("unknown", UUID.randomUUID()));
    }

    @Test
    void embedRowsWithBlankContentProducesNothing() {
        VectorIndexService.EmbedRows rows =
                service.embedRows(readyConfig(), VectorIndexService.TYPE_BUG, UUID.randomUUID(),
                        PROJECT_ID, USER_ID, "  \n ");

        assertTrue(rows.rows().isEmpty());
        assertEquals(0, rows.tokens());
        verifyNoInteractions(embeddingClient);
    }
}
