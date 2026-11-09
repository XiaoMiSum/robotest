package io.github.xiaomisum.robotest.service.ai.vector;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionControl;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 向量重建执行策略单测（总册 4.2 助手与向量策略口径 / 详设 4.4）：无 chat 模型仍可执行、
 * scope 子集与 versions 消费、重嵌失败先于索引写、steps 按 embedding 批次与替换动作计、
 * 共享闸门（预算 / 中止）在批次前拦截（C8）。
 */
@ExtendWith(MockitoExtension.class)
class VectorReindexExecutionStrategyTest {

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
    private ExecutionControl control;

    @InjectMocks
    private VectorReindexExecutionStrategy strategy;

    private TaskExecutionStrategy.ExecutionContext context;

    /** 纯单测环境无 MyBatis 初始化：wrapper 的 lambda 列解析依赖框架启动期注册的 TableInfo 缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : new Class<?>[]{AiEmbeddingConfig.class, AiVectorIndex.class,
                Requirement.class, Bug.class}) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }

    @BeforeEach
    void setUp() {
        AiTask task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setType("vector_reindex");
        task.setSubmittedBy(USER_ID);
        task.setInput(Map.of());
        AiTaskExecution execution = new AiTaskExecution();
        execution.setId(UUID.randomUUID());
        execution.setTaskId(task.getId());
        // 聊天模型豁免（总册 4.2）：执行上下文不携带模型，策略不得依赖 chat 选型
        context = new TaskExecutionStrategy.ExecutionContext(task, execution, null,
                UUID.randomUUID(), UUID.randomUUID(), null, null);
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
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(typmod);
        when(requirementMapper.selectList(any())).thenReturn(requirements);
        when(testCaseDocumentMapper.selectList(any())).thenReturn(documents);
        when(bugMapper.selectList(any())).thenReturn(bugs);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void executeWithoutChatModel_replacesAllTypesAndCountsBatchSteps() {
        AiEmbeddingConfig config = configWithVersions();
        stubFullExecution(config, ALIGNED_TYPMOD, List.of(requirement()), List.of(document()), List.of(bug()));
        when(embeddingMapper.selectSingleton()).thenReturn(config);
        when(vectorIndexService.contentFor(any(), any())).thenReturn("正文");
        when(vectorIndexService.embedRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(new VectorIndexService.EmbedRows(List.of(new AiVectorIndex()), 5));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals(3, result.resultMeta().get("entityCount"));
        assertEquals(3, result.resultMeta().get("chunkCount"));
        assertTrueEmptyProposals(result);
        assertNull(context.model(), "chat 模型豁免：策略全程不依赖执行模型");
        verify(vectorIndexService, times(3)).embedRows(any(), any(), any(), any(), any(), any());
        // steps 按 embedding 批次（3 次 embedBatch 调用）+ 索引替换动作（1）计，不按实体 / 分块行计
        verify(control, times(3)).afterModelCall(eq(5L), eq(0L), any(BigDecimal.class));
        verify(control, times(1)).afterModelCall(eq(0L), eq(0L), any(BigDecimal.class));
        verify(control, times(5)).beforeCall();
        verify(indexMapper, times(3)).delete(any());
        verify(indexMapper, times(3)).insertBatch(any());
        verify(embeddingMapper).update(isNull(), any());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void executeEmptyContentEntity_producesNoBatchStep() {
        AiEmbeddingConfig config = configWithVersions();
        stubFullExecution(config, ALIGNED_TYPMOD, List.of(requirement()), List.of(), List.of());
        when(vectorIndexService.contentFor(any(), any())).thenReturn(null);
        when(vectorIndexService.embedRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(new VectorIndexService.EmbedRows(List.of(), 0));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals(1, result.resultMeta().get("entityCount"));
        assertEquals(0, result.resultMeta().get("chunkCount"));
        // 空内容不产生 embedding 批次 → 不计步，仅替换动作一步
        verify(control, times(1)).afterModelCall(eq(0L), eq(0L), any(BigDecimal.class));
    }

    @Test
    void executeSubsetScopeKeepsVersionsForFullReindex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(ALIGNED_TYPMOD);
        when(bugMapper.selectList(any())).thenReturn(List.of());
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        context.task().setInput(Map.of("scope", Map.of("entityTypes", List.of("bug"))));

        strategy.execute(context, control);

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

        strategy.execute(context, control);

        verify(jdbcTemplate).execute(contains("vector(768)"));
    }

    @Test
    void executeEmbedFailurePropagatesBeforeTouchingIndex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(ALIGNED_TYPMOD);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement()));
        when(testCaseDocumentMapper.selectList(any())).thenReturn(List.of());
        when(bugMapper.selectList(any())).thenReturn(List.of());
        when(vectorIndexService.contentFor(any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));

        assertThrows(ServiceException.class, () -> strategy.execute(context, control));

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

        strategy.execute(context, control);

        // 重建期间配置再次变更，新待重建标记不可被误消费
        verify(embeddingMapper, never()).update(any(), any());
    }

    @Test
    void executeRequiresEnabledEmbedding() {
        when(gate.requireEnabled())
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED));

        ServiceException ex = assertThrows(ServiceException.class, () -> strategy.execute(context, control));

        assertEquals(ErrorCodeConstants.AI_EMBEDDING_NOT_CONFIGURED.code(), ex.getCode());
        verifyNoInteractions(control, indexMapper, embeddingMapper, requirementMapper,
                testCaseDocumentMapper, bugMapper, jdbcTemplate);
    }

    /** 共享护栏：预算闸门在下一批 embedding 前拦截（BudgetExceededException 上抛交分派器落 aborted） */
    @Test
    void execute_budgetExhaustedBeforeBatch_propagatesWithoutTouchingIndex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(ALIGNED_TYPMOD);
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement()));
        when(testCaseDocumentMapper.selectList(any())).thenReturn(List.of());
        when(bugMapper.selectList(any())).thenReturn(List.of());
        doNothing().doThrow(new ExecutionControl.BudgetExceededException())
                .when(control).beforeCall();

        assertThrows(ExecutionControl.BudgetExceededException.class,
                () -> strategy.execute(context, control));

        verify(vectorIndexService, never()).embedRows(any(), any(), any(), any(), any(), any());
        verify(indexMapper, never()).delete(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    /** 共享护栏：用户中止（终态竞争）后不上招建索引动作，异常原样交分派器忽略收尾 */
    @Test
    void execute_inactiveBeforeReplacing_propagatesWithoutTouchingIndex() {
        AiEmbeddingConfig config = configWithVersions();
        when(gate.requireEnabled()).thenReturn(config);
        // 第一次闸门（进入执行）即发现执行已被中止
        doThrow(new ExecutionControl.InactiveExecutionException()).when(control).beforeCall();

        assertThrows(ExecutionControl.InactiveExecutionException.class,
                () -> strategy.execute(context, control));

        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class));
        verify(indexMapper, never()).delete(any());
        verify(transactionManager, never()).getTransaction(any());
    }

    private static void assertTrueEmptyProposals(TaskExecutionStrategy.ExecutionResult result) {
        assertEquals(List.of(), result.proposals());
    }
}
