package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
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
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionControl;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * vector_reindex 执行策略（总册 4.2 / 详设 4.4）：确定性重嵌与索引替换——先按当前配置
 * 完成全部分块重嵌（外部调用失败即任务失败、索引未动），再在单一短事务内按类型整体替换
 * 索引并消费 versions；仅全量完成才清 versions 恢复检索门禁。
 *
 * <p>豁免 chat 模型选型（不使用 {@code ctx.model()}，执行行 model_id 为空）；
 * 仅要求向量能力可用（{@code gate.requireEnabled}，重建中本身即任务起因不再拦截）。
 * 共享护栏：steps 按 embedding 批次与索引替换动作计（空内容实体不产生批次不计步、
 * 分块行不计步），每批前走闸门（中止 / 预算 / 心跳），时长与 token 维度随心跳判定。</p>
 */
@Component
public class VectorReindexExecutionStrategy implements TaskExecutionStrategy {

    /** 单条 INSERT 的行上限：每行十余参数，留足 PG 驱动参数上限的余量 */
    private static final int INSERT_BATCH_SIZE = 500;

    /** pgvector 的 typmod = 维度 + varlena 头（4 字节），-1 表示未定长 */
    private static final int VECTOR_TYPMOD_HEADER = 4;

    @Resource
    private AiEmbeddingGate gate;
    @Resource
    private VectorIndexService vectorIndexService;
    @Resource
    private AiVectorIndexMapper indexMapper;
    @Resource
    private AiEmbeddingConfigMapper embeddingMapper;
    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public ExecutionResult execute(ExecutionContext ctx, ExecutionControl control) {
        AiEmbeddingConfig config = gate.requireEnabled();
        Set<String> types = VectorReindexTypeAdapter.resolveTypes(ctx.task().getInput());
        control.beforeCall();

        // HNSW 索引要求定长维度：维度变更后的重建先切列（schema 注释「n = dimensions」口径）
        alignColumnDimensions(config.getDimensions());

        List<Draft> drafts = loadDrafts(types);
        Map<String, List<AiVectorIndex>> rowsByType = new LinkedHashMap<>();
        long tokens = 0;
        int chunkCount = 0;
        for (Draft draft : drafts) {
            // 每批 embedding 前走闸门：中止或预算耗尽时不进入下一批，索引保持未动
            control.beforeCall();
            String content = vectorIndexService.contentFor(draft.type(), draft.entityId());
            VectorIndexService.EmbedRows embedRows = vectorIndexService.embedRows(
                    config, draft.type(), draft.entityId(), draft.projectId(),
                    ctx.task().getSubmittedBy(), content);
            if (embedRows.rows().isEmpty()) {
                continue;
            }
            rowsByType.computeIfAbsent(draft.type(), key -> new ArrayList<>()).addAll(embedRows.rows());
            tokens += embedRows.tokens();
            chunkCount += embedRows.rows().size();
            // 一次 embedRows = 一个 embedding 批次 = 一步（空内容不产生批次不计步，分块行不计步）
            control.afterModelCall(embedRows.tokens(), 0, BigDecimal.ZERO);
        }

        // 替换前最后闸门：中止后不得写索引（外部中止 / 预算耗尽 → aborted，索引不动）
        control.beforeCall();
        List<Map<String, Object>> versionsSnapshot = config.getVersions();
        boolean full = types.containsAll(VectorIndexService.ALL_TYPES);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> replaceIndex(types, rowsByType, full, versionsSnapshot));
        // 单一短事务整体替换 = 一次索引替换动作，计一步
        control.afterModelCall(0, 0, BigDecimal.ZERO);

        return new ExecutionResult(List.of(),
                Map.of("entityCount", drafts.size(), "chunkCount", chunkCount));
    }

    /** 列维度与配置对齐：不同才 ALTER（同维度走 PG 的 typmod 相等快路径，不重写表） */
    private void alignColumnDimensions(int dimensions) {
        Integer typmod = jdbcTemplate.queryForObject(
                "SELECT atttypmod FROM pg_attribute "
                        + "WHERE attrelid = 'ai_vector_index'::regclass AND attname = 'embedding' AND NOT attisdropped",
                Integer.class);
        if (typmod != null && typmod >= 0 && typmod - VECTOR_TYPMOD_HEADER == dimensions) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE ai_vector_index ALTER COLUMN embedding TYPE vector(" + dimensions + ")"
                + " USING embedding::vector(" + dimensions + ")");
    }

    /** 扫描入索引实体：需求排除已归档（4.4 归档移出索引）；测试 / 缺陷按文档与主表全量 */
    private List<Draft> loadDrafts(Set<String> types) {
        List<Draft> drafts = new ArrayList<>();
        if (types.contains(VectorIndexService.TYPE_REQUIREMENT)) {
            requirementMapper.selectList(new LambdaQueryWrapperX<Requirement>()
                            .ne(Requirement::getStatus, Constants.RequirementStatus.ARCHIVED))
                    .forEach(row -> drafts.add(new Draft(VectorIndexService.TYPE_REQUIREMENT,
                            row.getId(), row.getProjectId())));
        }
        if (types.contains(VectorIndexService.TYPE_TESTCASE)) {
            testCaseDocumentMapper.selectList(new LambdaQueryWrapperX<>())
                    .forEach(row -> drafts.add(new Draft(VectorIndexService.TYPE_TESTCASE,
                            row.getId(), row.getProjectId())));
        }
        if (types.contains(VectorIndexService.TYPE_BUG)) {
            bugMapper.selectList(new LambdaQueryWrapperX<Bug>())
                    .forEach(row -> drafts.add(new Draft(VectorIndexService.TYPE_BUG,
                            row.getId(), row.getProjectId())));
        }
        return drafts;
    }

    /**
     * 短事务替换：按类型先逻辑删旧再批量插新；全量且 versions 未在重建期间被再次修改
     * 才清 versions（配置中途变更留下的新待重建标记不能被误消费）。
     */
    private void replaceIndex(Set<String> types, Map<String, List<AiVectorIndex>> rowsByType,
            boolean full, List<Map<String, Object>> versionsSnapshot) {
        for (String entityType : VectorIndexService.ALL_TYPES) {
            if (!types.contains(entityType)) {
                continue;
            }
            indexMapper.delete(new LambdaQueryWrapperX<AiVectorIndex>()
                    .eq(AiVectorIndex::getEntityType, entityType));
            List<AiVectorIndex> rows = rowsByType.getOrDefault(entityType, List.of());
            for (int from = 0; from < rows.size(); from += INSERT_BATCH_SIZE) {
                int to = Math.min(from + INSERT_BATCH_SIZE, rows.size());
                indexMapper.insertBatch(rows.subList(from, to));
            }
        }
        if (!full) {
            // 按 scope 子集重建不足以证明整体索引已刷新，requiresReindex 保持到全量重建收口
            return;
        }
        AiEmbeddingConfig current = embeddingMapper.selectSingleton();
        if (current == null || !Objects.equals(current.getVersions(), versionsSnapshot)) {
            return;
        }
        embeddingMapper.update(null, new LambdaUpdateWrapperX<AiEmbeddingConfig>()
                .eq(AiEmbeddingConfig::getId, current.getId())
                .set(AiEmbeddingConfig::getVersions, List.of()));
    }

    private record Draft(String type, UUID entityId, UUID projectId) {
    }
}
