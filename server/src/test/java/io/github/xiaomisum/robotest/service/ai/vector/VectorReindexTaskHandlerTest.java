package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiEmbeddingConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiVectorIndex;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.repository.ai.AiEmbeddingConfigMapper;
import io.github.xiaomisum.robotest.repository.ai.AiVectorIndexMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorReindexTaskHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    /** pgvector typmod = 维度 + 4（varlena 头），772 表示 vector(768) 已对齐 */
    private static final int ALIGNED_TYPMOD = 772;

    @Mock
    private AiEmbeddingGate gate;
    @Mock
    private VectorIndexService vectorIndexService;
    @Mock
    private AiVectorIndexMapper indexMapper;
    @Mock
    private AiEmbeddingConfigMapper embeddingMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private VectorReindexTaskHandler handler;

    /** 纯单测环境无 MyBatis 初始化：wrapper 的 lambda 列解析依赖框架启动期注册的 TableInfo 缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : new Class<?>[]{AiEmbeddingConfig.class, AiVectorIndex.class, Requirement.class}) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }

    private static AiEmbeddingConfig configWithVersions() {
        AiEmbeddingConfig config = new AiEmbeddingConfig();
        config.setEnabled(true);
        config.setEmbeddingModel("text-embedding-3");
        config.setDimensions(768);
        config.setOperator("cosine");
        config.setVersions(List.of(Map.of("version", 1)));
        return config;
    }

    private static Requirement requirement() {
        Requirement row = new Requirement();
        row.setId(UUID.randomUUID());
        row.setProjectId(UUID.randomUUID());
        return row;
    }

    private static TestCaseDocument document() {
        TestCaseDocument row = new TestCaseDocument();
        row.setId(UUID.randomUUID());
        row.setProjectId(UUID.randomUUID());
        return row;
    }

    private static Bug bug() {
        Bug row = new Bug();
        row.setId(UUID.randomUUID());
        row.setProjectId(UUID.randomUUID());
        return row;
    }

    /** 全量范围的执行就绪桩（versions 消费由各测试自行断言，故 selectSingleton 不在此） */
    private void stubFullExecution(AiEmbeddingConfig config, Integer typmod, List<Requirement> requirements,
            List<TestCaseDocument> documents, List<Bug> bugs) {
        when(gate.requireEnabled()).thenReturn(config);
        when(context.getInput()).thenReturn(Map.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(typmod);
        when(requirementMapper.selectList(any())).thenReturn(requirements);
        when(testCaseDocumentMapper.selectList(any())).thenReturn(documents);
        when(bugMapper.selectList(any())).thenReturn(bugs);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void typeAndScopeValidation() {
        assertEquals("vector_reindex", handler.type());
        // 缺省 / 未带 entityTypes = 全量
        handler.validateInput(null);
        handler.validateInput(Map.of());
        handler.validateInput(Map.of("scope", Map.of()));
        handler.validateInput(Map.of("scope", Map.of("entityTypes", List.of("bug"))));

        ServiceException scopeType = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("scope", "requirement")));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), scopeType.getCode());

        ServiceException empty = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("scope", Map.of("entityTypes", List.of()))));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), empty.getCode());

        ServiceException illegal = assertThrows(ServiceException.class,
                () -> handler.validateInput(Map.of("scope", Map.of("entityTypes", List.of("foo")))));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), illegal.getCode());
    }

    @Test
    void executeFullReindexReplacesAllTypesAndConsumesVersions() {
        AiEmbeddingConfig config = configWithVersions();
        stubFullExecution(config, ALIGNED_TYPMOD, List.of(requirement()), List.of(document()), List.of(bug()));
        when(embeddingMapper.selectSingleton()).thenReturn(config);
        when(vectorIndexService.contentFor(any(), any())).thenReturn("正文");
        when(vectorIndexService.embedRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(new VectorIndexService.EmbedRows(List.of(new AiVectorIndex()), 5));
        when(context.getUserId()).thenReturn(USER_ID);

        TaskResult result = handler.execute(context);

        assertEquals(Integer.valueOf(3), result.result().get("entityCount"));
        assertEquals(Integer.valueOf(3), result.result().get("chunkCount"));
        assertEquals(15, result.tokensIn());
        verify(vectorIndexService, times(3)).contentFor(any(), any());
        verify(vectorIndexService, times(3)).embedRows(any(), any(), any(), any(), any(), any());
        // 短事务内按类型先逻辑删后批量插，单实体各一批
        verify(indexMapper, times(3)).delete(any());
        verify(indexMapper, times(3)).insertBatch(any());
        verify(embeddingMapper).update(isNull(), any());
        verify(jdbcTemplate, never()).execute(anyString());
        verify(context, atLeastOnce()).report(anyInt(), anyString());
    }

    @Test
    void executeSubsetScopeKeepsVersionsForFullReindex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        when(context.getInput()).thenReturn(Map.of("scope", Map.of("entityTypes", List.of("bug"))));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(ALIGNED_TYPMOD);
        when(bugMapper.selectList(any())).thenReturn(List.of());
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        handler.execute(context);

        verify(requirementMapper, never()).selectList(any());
        verify(testCaseDocumentMapper, never()).selectList(any());
        verify(indexMapper, times(1)).delete(any());
        // 子集重建不证明整体索引已刷新：不清 versions
        verify(embeddingMapper, never()).selectSingleton();
        verify(embeddingMapper, never()).update(any(), any());
    }

    @Test
    void executeAltersColumnWhenTypmodMismatches() {
        AiEmbeddingConfig config = configWithVersions();
        // typmod 1540 = vector(1536)，与配置 768 不符 → 先切列
        stubFullExecution(config, 1536 + 4, List.of(), List.of(), List.of());
        when(embeddingMapper.selectSingleton()).thenReturn(config);

        handler.execute(context);

        verify(jdbcTemplate).execute(contains("vector(768)"));
    }

    @Test
    void executeEmbedFailurePropagatesBeforeTouchingIndex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        when(context.getInput()).thenReturn(Map.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(ALIGNED_TYPMOD);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement()));
        when(testCaseDocumentMapper.selectList(any())).thenReturn(List.of());
        when(bugMapper.selectList(any())).thenReturn(List.of());
        when(vectorIndexService.contentFor(any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));

        assertThrows(ServiceException.class, () -> handler.execute(context));

        // 重嵌失败在事务之前上抛：索引与 versions 均未动
        verify(indexMapper, never()).delete(any());
        verify(indexMapper, never()).insertBatch(any());
        verify(transactionManager, never()).getTransaction(any());
        verify(embeddingMapper, never()).update(any(), any());
    }

    @Test
    void executeKeepsVersionsWhenChangedMidRebuild() {
        AiEmbeddingConfig config = configWithVersions();
        stubFullExecution(config, ALIGNED_TYPMOD, List.of(), List.of(), List.of());
        AiEmbeddingConfig changed = configWithVersions();
        changed.setVersions(List.of(Map.of("version", 2)));
        when(embeddingMapper.selectSingleton()).thenReturn(changed);

        handler.execute(context);

        // 重建期间配置再次变更，新待重建标记不可被误消费
        verify(embeddingMapper, never()).update(any(), any());
    }

    @Test
    void executeRequiresEnabledEmbedding() {
        when(gate.requireEnabled())
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED));

        ServiceException ex = assertThrows(ServiceException.class, () -> handler.execute(context));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), ex.getCode());
        verifyNoInteractions(indexMapper, embeddingMapper, requirementMapper,
                testCaseDocumentMapper, bugMapper, jdbcTemplate);
    }
}
