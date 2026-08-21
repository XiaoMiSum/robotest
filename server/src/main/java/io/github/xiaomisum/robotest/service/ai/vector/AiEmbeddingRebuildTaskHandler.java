package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.support.AiTaskProgressSupport;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * embedding_rebuild 任务处理器（详细设计 4.4）：
 * DDL 对齐维度并清空存量向量 → 逐项目分批回填（50 条/批 Embedding，UPSERT）→ 每批进度心跳 + 协作式取消。
 * 维度变更时 DROP 索引 → TRUNCATE → ALTER 列类型 → 重建索引；仅模型变更时直接 TRUNCATE 复用索引。
 */
@Slf4j
@Component
public class AiEmbeddingRebuildTaskHandler implements AiTaskHandler {

    public static final String TYPE = Constants.AiTaskType.EMBEDDING_REBUILD;

    /** 单批文本数（详细设计 4.4：批量 50 条/次 Embedding 调用） */
    static final int BATCH_SIZE = 50;
    /** 单条失败率超该阈值任务置 failed（详细设计 4.4） */
    static final double FAILURE_RATE_LIMIT = 0.2;
    /** pgvector 存储 vector typmod = 维度 + VARHDRSZ */
    private static final int VARHDRSZ = 4;

    @Resource
    private AiVectorSearchService vectorSearchService;
    @Resource
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Map<String, Object> execute(AiAnalysisTask task) {
        int dimension = vectorSearchService.configuredEmbeddingDimension();
        if (dimension <= 0) {
            throw new IllegalStateException("Embedding 配置缺失，无法执行向量重建");
        }
        String model = vectorSearchService.embeddingModel();

        prepareSchema(dimension);

        List<IndexJob> jobs = collectJobs();
        int total = jobs.size();
        if (total == 0) {
            return buildResult(0, 0, 0, 0);
        }

        int processed = 0;
        int failed = 0;
        for (int start = 0; start < jobs.size(); start += BATCH_SIZE) {
            List<IndexJob> batch = jobs.subList(start, Math.min(start + BATCH_SIZE, jobs.size()));
            // 批次边界心跳：写入进度与累计结果；影响行数为 0 表示任务已取消/置失败，立即中止返回部分结果（4.6）
            if (AiTaskProgressSupport.heartbeat(aiAnalysisTaskMapper, task.getId(),
                    AiTaskProgressSupport.percent(processed, total),
                    JsonUtils.toJsonString(buildResult(0, 0, processed - failed, failed))) == 0) {
                return buildResult(0, 0, processed - failed, failed);
            }
            try {
                List<float[]> vectors = vectorSearchService.embedBatch(batch.stream()
                        .map(IndexJob::text).toList());
                if (vectors.size() != batch.size()) {
                    throw new IllegalStateException("Embedding 返回数量不匹配: " + vectors.size() + "/" + batch.size());
                }
                for (int i = 0; i < batch.size(); i++) {
                    IndexJob job = batch.get(i);
                    String hash = vectorSearchService.buildSourceHash(model, job.text());
                    if (job.bugId() != null) {
                        vectorSearchService.upsertBug(job.bugId(), job.projectId(), vectors.get(i), hash);
                    } else {
                        vectorSearchService.upsertCase(job.nodeId(), job.projectId(), vectors.get(i), hash);
                    }
                }
            } catch (Exception e) {
                log.warn("[AI] 向量重建批次失败，跳过留给补偿: {}", e.getMessage());
                failed += batch.size();
                if ((double) failed / total > FAILURE_RATE_LIMIT) {
                    throw new IllegalStateException("向量重建失败率超阈值: " + failed + "/" + total);
                }
            }
            processed += batch.size();
        }
        return buildResult(bugCount(jobs), caseCount(jobs), processed - failed, failed);
    }

    /**
     * DDL 对齐：维度一致仅 TRUNCATE 复用索引；维度变更走 DROP 索引 → TRUNCATE → ALTER → 重建索引（4.4）
     */
    private void prepareSchema(int dimension) {
        int actualDimension = currentColumnDimension();
        if (actualDimension != dimension) {
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_ai_bug_embedding_hnsw");
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_ai_case_embedding_hnsw");
            jdbcTemplate.execute("TRUNCATE TABLE ai_bug_embedding, ai_case_embedding");
            jdbcTemplate.execute("ALTER TABLE ai_bug_embedding ALTER COLUMN embedding TYPE vector(" + dimension + ")");
            jdbcTemplate.execute("ALTER TABLE ai_case_embedding ALTER COLUMN embedding TYPE vector(" + dimension + ")");
        } else {
            jdbcTemplate.execute("TRUNCATE TABLE ai_bug_embedding, ai_case_embedding");
        }
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_bug_embedding_hnsw ON ai_bug_embedding USING hnsw (embedding vector_cosine_ops)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ai_case_embedding_hnsw ON ai_case_embedding USING hnsw (embedding vector_cosine_ops)");
    }

    private int currentColumnDimension() {
        try {
            Integer typmod = jdbcTemplate.queryForObject(
                    "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'ai_bug_embedding'::regclass AND attname = 'embedding'",
                    Integer.class);
            return typmod != null && typmod > VARHDRSZ ? typmod - VARHDRSZ : 0;
        } catch (Exception e) {
            log.warn("[AI] 查询向量列维度失败，按不匹配处理执行 ALTER: {}", e.getMessage());
            return 0;
        }
    }

    private List<IndexJob> collectJobs() {
        List<IndexJob> jobs = new ArrayList<>();
        for (Project project : projectMapper.listAll()) {
            for (Bug bug : bugMapper.findOpenBugsByProjectId(project.getId())) {
                jobs.add(new IndexJob(bug.getId(), null, project.getId(),
                        vectorSearchService.buildBugSourceText(bug)));
            }
            for (TestCaseDocument document : testCaseDocumentMapper.listByProjectId(project.getId())) {
                List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(document.getId());
                Map<UUID, String> texts = vectorSearchService.buildCaseIndexTexts(document.getName(), nodes);
                texts.forEach((nodeId, text) ->
                        jobs.add(new IndexJob(null, nodeId, project.getId(), text)));
            }
        }
        return jobs;
    }

    private int bugCount(List<IndexJob> jobs) {
        return (int) jobs.stream().filter(j -> j.bugId() != null).count();
    }

    private int caseCount(List<IndexJob> jobs) {
        return (int) jobs.stream().filter(j -> j.nodeId() != null).count();
    }

    private Map<String, Object> buildResult(int bugCount, int caseCount, int embeddedCount, int failedCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bugCount", bugCount);
        result.put("caseCount", caseCount);
        result.put("embeddedCount", embeddedCount);
        result.put("failedCount", failedCount);
        return result;
    }

    private record IndexJob(UUID bugId, UUID nodeId, UUID projectId, String text) {
    }
}
