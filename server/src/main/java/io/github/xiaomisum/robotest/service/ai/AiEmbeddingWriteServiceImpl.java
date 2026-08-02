package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.BugEmbedding;
import io.github.xiaomisum.robotest.model.entity.ai.CaseEmbedding;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.BugEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.ai.CaseEmbeddingMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 向量增量写入与补偿实现（详细设计 4.1）。
 *
 * <p>补偿扫描按项目分组、每轮上限 200 条避免长时间占用；重建互斥先于一切写入；
 * Redis 锁（SET NX + TTL）保证多实例单执行，锁丢失兜底由下一轮补偿覆盖。</p>
 */
@Slf4j
@Service
public class AiEmbeddingWriteServiceImpl implements AiEmbeddingWriteService {

    /** 补偿每轮嵌入上限（详细设计 4.1，避免长时间占用） */
    private static final int COMPENSATE_ROUND_LIMIT = 200;
    private static final String COMPENSATE_LOCK_KEY = "ai:embedding:compensate:lock";
    private static final Duration COMPENSATE_LOCK_TTL = Duration.ofMinutes(10);

    @Resource
    private AiVectorSearchService vectorSearchService;
    @Resource
    private AiAnalysisTaskMapper aiAnalysisTaskMapper;
    @Resource
    private BugMapper bugMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private BugEmbeddingMapper bugEmbeddingMapper;
    @Resource
    private CaseEmbeddingMapper caseEmbeddingMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private StringRedisTemplate redisTemplate;

    @Override
    public void handleBugChanged(Bug bug) {
        if (isRebuildInProgress()) {
            return;
        }
        if (bug == null || bug.getId() == null) {
            return;
        }
        if (Constants.BugStatus.CLOSED.equals(bug.getStatus())) {
            vectorSearchService.deleteBugIndex(bug.getId());
            return;
        }
        try {
            vectorSearchService.indexBug(bug);
        } catch (Exception e) {
            log.warn("[AI] 缺陷向量增量写入异常（留待补偿）bugId={}: {}", bug.getId(), e.getMessage());
        }
    }

    @Override
    public void handleBugDeleted(UUID bugId) {
        if (bugId != null) {
            vectorSearchService.deleteBugIndex(bugId);
        }
    }

    @Override
    public void handleCaseChanged(TestCaseNode node) {
        if (isRebuildInProgress()) {
            return;
        }
        if (node == null || node.getId() == null) {
            return;
        }
        try {
            vectorSearchService.indexCase(node);
        } catch (Exception e) {
            log.warn("[AI] 用例向量增量写入异常（留待补偿）nodeId={}: {}", node.getId(), e.getMessage());
        }
    }

    @Override
    public void handleCaseDeleted(UUID nodeId) {
        if (nodeId != null) {
            vectorSearchService.deleteCaseIndex(nodeId);
        }
    }

    @Override
    public void handleCasesDeleted(Collection<UUID> nodeIds) {
        vectorSearchService.deleteCaseIndexes(nodeIds);
    }

    /**
     * 补偿定时任务：每 10 分钟执行；重建互斥 + Redis 锁（SET NX + TTL）。
     */
    @Scheduled(fixedDelay = 600_000)
    public void compensateScheduled() {
        compensate();
    }

    @Override
    public void compensate() {
        if (isRebuildInProgress()) {
            return;
        }
        String instanceId = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                COMPENSATE_LOCK_KEY, instanceId, COMPENSATE_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            doCompensate();
        } catch (Exception e) {
            log.warn("[AI] 向量补偿异常: {}", e.getMessage());
        } finally {
            releaseLock(instanceId);
        }
    }

    private void doCompensate() {
        int remaining = COMPENSATE_ROUND_LIMIT;
        for (Project project : projectMapper.listAll()) {
            if (remaining <= 0) {
                break;
            }
            remaining -= compensateBugs(project.getId(), remaining);
            remaining -= compensateCases(project.getId(), remaining);
        }
    }

    private int compensateBugs(UUID projectId, int limit) {
        List<Bug> bugs = bugMapper.findOpenBugsByProjectId(projectId);
        if (bugs.isEmpty()) {
            return 0;
        }
        String model = vectorSearchService.embeddingModel();
        if (!StringUtils.hasText(model)) {
            return 0;
        }
        Map<UUID, String> stored = bugEmbeddingMapper.findHashesByBugIds(bugs.stream()
                        .map(Bug::getId).toList()).stream()
                .collect(Collectors.toMap(BugEmbedding::getBugId, BugEmbedding::getSourceHash, (a, b) -> a));
        int embedded = 0;
        for (Bug bug : bugs) {
            if (embedded >= limit) {
                break;
            }
            String text = vectorSearchService.buildBugSourceText(bug);
            String expected = vectorSearchService.buildSourceHash(model, text);
            if (expected.equals(stored.get(bug.getId()))) {
                continue;
            }
            if (vectorSearchService.indexBug(bug)) {
                embedded++;
            }
        }
        return embedded;
    }

    private int compensateCases(UUID projectId, int limit) {
        List<TestCaseModule> documents = testCaseModuleMapper.findDocumentModulesByProjectId(projectId);
        int embedded = 0;
        for (TestCaseModule document : documents) {
            if (embedded >= limit) {
                break;
            }
            List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(document.getId());
            Map<UUID, String> texts = vectorSearchService.buildCaseIndexTexts(document.getName(), nodes);
            if (texts.isEmpty()) {
                continue;
            }
            String model = vectorSearchService.embeddingModel();
            if (!StringUtils.hasText(model)) {
                return embedded;
            }
            Map<UUID, String> stored = caseEmbeddingMapper.findHashesByNodeIds(texts.keySet()).stream()
                    .collect(Collectors.toMap(CaseEmbedding::getNodeId, CaseEmbedding::getSourceHash, (a, b) -> a));
            for (Map.Entry<UUID, String> entry : texts.entrySet()) {
                if (embedded >= limit) {
                    break;
                }
                String expected = vectorSearchService.buildSourceHash(model, entry.getValue());
                if (expected.equals(stored.get(entry.getKey()))) {
                    continue;
                }
                TestCaseNode node = findNode(nodes, entry.getKey());
                if (node != null && vectorSearchService.indexCase(node)) {
                    embedded++;
                }
            }
        }
        return embedded;
    }

    private TestCaseNode findNode(List<TestCaseNode> nodes, UUID nodeId) {
        return nodes.stream().filter(n -> n.getId().equals(nodeId)).findFirst().orElse(null);
    }

    private boolean isRebuildInProgress() {
        return aiAnalysisTaskMapper.hasInProgressRebuild();
    }

    private void releaseLock(String instanceId) {
        try {
            String current = redisTemplate.opsForValue().get(COMPENSATE_LOCK_KEY);
            if (instanceId.equals(current)) {
                redisTemplate.delete(COMPENSATE_LOCK_KEY);
            }
        } catch (Exception e) {
            log.warn("[AI] 补偿锁释放失败（TTL 兜底）: {}", e.getMessage());
        }
    }
}
