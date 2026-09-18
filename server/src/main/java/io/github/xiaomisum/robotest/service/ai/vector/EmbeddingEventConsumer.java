package io.github.xiaomisum.robotest.service.ai.vector;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.service.domain.bug.BugChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 缺陷变更事件消费者：在 service.ai 内部订阅，把 bug 域发布的事件交给向量写入。
 * AFTER_COMMIT + fallback 保证"事务提交后"时序（无事务时同步兜底），与重构前
 * BugServiceImpl 手工 afterCommit 行为一致；消费端重查最新行，与重构前 update
 * 路径"selectById 重查再写"同源（关闭分支重查到的 CLOSED 行即触发删向量）。
 */
@Component
public class EmbeddingEventConsumer {

    private final BugMapper bugMapper;
    private final AiEmbeddingWriteService aiEmbeddingWriteService;

    public EmbeddingEventConsumer(BugMapper bugMapper, AiEmbeddingWriteService aiEmbeddingWriteService) {
        this.bugMapper = bugMapper;
        this.aiEmbeddingWriteService = aiEmbeddingWriteService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBugChanged(BugChangedEvent event) {
        // 缺陷已不存在（逻辑删除/补偿空窗）则跳过写入：一致性由 AiEmbeddingWriteService 内部补偿兜底
        Bug bug = bugMapper.selectById(event.bugId());
        if (bug == null) {
            return;
        }
        aiEmbeddingWriteService.handleBugChanged(bug);
    }
}