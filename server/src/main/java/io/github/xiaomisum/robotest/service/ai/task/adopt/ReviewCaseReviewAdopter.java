package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ProposalAdopter;
import io.github.xiaomisum.robotest.service.domain.review.ReviewEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewSnapshotService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewWorkflow;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * AI 评审建议承接（详设 4.3）：采纳只新增一条标记记录并更新快照节点最后标记，
 * 不改关联集合与既有记录；采纳记录由任务资源服务统一写入。
 */
@Service
public class ReviewCaseReviewAdopter implements ProposalAdopter {

    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private ReviewSnapshotService reviewSnapshotService;
    @Resource
    private ReviewWorkflow reviewWorkflow;
    @Resource
    private TestReviewRecordMapper reviewRecordMapper;

    @Override
    public String type() {
        return Constants.AiTaskType.REVIEW_CASE_REVIEW;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiProposalAction.REJECTED.equals(context.action())) {
            return null;
        }
        UUID reviewId = parseUuid(AssistAdoptSupport.taskInput(context).get("reviewId"));
        if (reviewId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null || !context.projectId().equals(review.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_TASK_NOT_FOUND);
        }
        if (!reviewWorkflow.can(review, ReviewEvent.SUBMIT_RECORD)) {
            // 终态评审的既有提案不可再写回（详设 4.4）
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID);
        }

        UUID snapshotNodeId = parseUuid(context.proposalContent().get("snapshotNodeId"));
        if (snapshotNodeId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        TestReviewNodeSnapshot node = reviewSnapshotService.getNode(snapshotNodeId);
        if (node == null || !reviewId.equals(node.getReviewId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        if (!Constants.NodeType.CASE.equals(node.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ONLY_CASE_NODE_CAN_MARK_REVIEW);
        }

        boolean edited = Constants.AiProposalAction.ADOPTED_EDITED.equals(context.action());
        Map<String, Object> payload = edited ? context.content() : context.proposalContent();
        if (payload == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        String mark = trimToNull(asString(payload.get("suggestedMark")));
        if (!Constants.ReviewMark.PASS.equals(mark) && !Constants.ReviewMark.FAIL.equals(mark)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        String reason = asString(payload.get("reason"));

        TestReviewRecord record = new TestReviewRecord();
        record.setReviewId(reviewId);
        record.setSnapshotNodeId(snapshotNodeId);
        record.setReviewerId(context.operatorId());
        record.setOperationType(Constants.ReviewOperation.MARK);
        record.setMark(mark);
        record.setComment("[AI 建议] " + (reason == null ? "" : reason));
        reviewRecordMapper.insert(record);
        reviewSnapshotService.applyMark(snapshotNodeId, context.operatorId(), mark, LocalDateTime.now());

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("recordId", String.valueOf(record.getId()));
        adoptedRef.put("snapshotNodeId", snapshotNodeId.toString());
        return new AdoptOutcome(record.getId(), adoptedRef);
    }
}
