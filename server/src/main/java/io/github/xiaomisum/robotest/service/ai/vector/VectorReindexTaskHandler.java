package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
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
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 向量索引全量重建处理器（详设 3.6.1 vector_reindex / 4.4）：
 * 先按当前配置完成全部分块重嵌（外部调用失败即任务失败、索引未动），再在单一短事务内
 * 按类型整体替换索引并消费 versions —— 读者在 MVCC 快照下看不到清空窗口；
 * 仅全量（scope 缺省或覆盖全部类型）完成才清 versions 恢复检索门禁。
 */
@Component
public class VectorReindexTaskHandler implements TaskHandler {

    static final String TYPE = "vector_reindex";

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
    public String type() {
        return TYPE;
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        resolveTypes(input);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        AiEmbeddingConfig config = gate.requireEnabled();
        Set<String> types = resolveTypes(context.getInput());

        // HNSW 索引要求定长维度：维度变更后的重建先切列（schema 注释「n = dimensions」口径）
        alignColumnDimensions(config.getDimensions());

        context.report(0, "读取实体");
        List<Draft> drafts = loadDrafts(types);
        Map<String, List<AiVectorIndex>> rowsByType = new LinkedHashMap<>();
        long tokens = 0;
        int chunkCount = 0;
        int processed = 0;
        int total = Math.max(drafts.size(), 1);
        for (Draft draft : drafts) {
            String content = vectorIndexService.contentFor(draft.type(), draft.entityId());
            VectorIndexService.EmbedRows embedRows = vectorIndexService.embedRows(
                    config, draft.type(), draft.entityId(), draft.projectId(), context.getUserId(), content);
            rowsByType.computeIfAbsent(draft.type(), key -> new ArrayList<>()).addAll(embedRows.rows());
            tokens += embedRows.tokens();
            chunkCount += embedRows.rows().size();
            processed++;
            context.report(Math.min(99, processed * 100 / total), "重嵌");
        }

        context.report(99, "写入索引");
        List<Map<String, Object>> versionsSnapshot = config.getVersions();
        boolean full = types.containsAll(VectorIndexService.ALL_TYPES);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> replaceIndex(types, rowsByType, full, versionsSnapshot));

        context.report(100, "完成");
        return new TaskResult(Map.of("entityCount", drafts.size(), "chunkCount", chunkCount),
                clamp(tokens), 0);
    }

    /**
     * 解析详设 3.6.1 的 scope：缺省 / 未带 entityTypes = 全量；
     * entityTypes 为白名单数组，非法取值报 1000018115。
     */
    private static Set<String> resolveTypes(Map<String, Object> input) {
        Object scope = input == null ? null : input.get("scope");
        if (scope == null) {
            return new LinkedHashSet<>(VectorIndexService.ALL_TYPES);
        }
        if (!(scope instanceof Map<?, ?> scopeMap)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                    "scope 须为对象 { entityTypes: [...] }");
        }
        Object raw = scopeMap.get("entityTypes");
        if (raw == null) {
            return new LinkedHashSet<>(VectorIndexService.ALL_TYPES);
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                    "scope.entityTypes 缺省全量重建，显式传值须为非空数组");
        }
        Set<String> types = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String entity) || !VectorIndexService.ALL_TYPES.contains(entity)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                        "scope.entityTypes 仅支持 requirement / testcase / bug");
            }
            types.add(entity);
        }
        return types;
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

    private static int clamp(long tokens) {
        return tokens > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tokens;
    }

    private record Draft(String type, UUID entityId, UUID projectId) {
    }
}
