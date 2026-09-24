package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestReviewConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewProgressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewRecord;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewLifecycleEvent;
import io.github.xiaomisum.robotest.service.domain.review.ReviewSnapshotService;
import io.github.xiaomisum.robotest.service.domain.review.ReviewStatus;
import io.github.xiaomisum.robotest.service.domain.review.ReviewWorkflow;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestReviewServiceImpl implements TestReviewService {

    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestReviewRecordMapper reviewRecordMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ReviewWorkflow reviewWorkflow;
    @Resource
    private ReviewSnapshotService reviewSnapshotService;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private ProjectActivityService projectActivityService;

    @Override
    public PageResult<TestReviewListRespDTO> getReviewPage(UUID projectId, UUID userId, String status,
            String keyword, Integer pageNo, Integer pageSize) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        PageResult<TestReview> page = testReviewMapper.findPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, projectId, keyword, status);

        // 列表展示进度/通过率：批量查本页全部关联用例快照，避免逐行 N+1
        List<UUID> reviewIds = page.getList().stream().map(TestReview::getId).toList();
        Map<UUID, List<TestReviewNodeSnapshot>> snapshotsByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewSnapshotService.listAssociatedByReviewIds(reviewIds, Constants.NodeType.CASE)
                        .stream().collect(Collectors.groupingBy(TestReviewNodeSnapshot::getReviewId));

        List<TestReviewListRespDTO> dtos = page.getList().stream().map(review -> {
            TestReviewListRespDTO dto = new TestReviewListRespDTO();
            dto.setId(review.getId());
            dto.setTitle(review.getTitle());
            dto.setStatus(review.getStatus());
            dto.setCreatedAt(review.getCreatedAt());

            SysUser initiator = userMapper.selectById(review.getInitiatorId());
            if (initiator != null) {
                TestReviewListRespDTO.InitiatorInfo info = new TestReviewListRespDTO.InitiatorInfo();
                info.setId(initiator.getId());
                info.setName(initiator.getUsername());
                dto.setInitiator(info);
            }

            List<UUID> participantIds = review.getParticipantIds() != null
                    ? review.getParticipantIds()
                    : new ArrayList<>();
            dto.setParticipantCount(participantIds.size());

            List<TestReviewNodeSnapshot> snapshots = snapshotsByReview.getOrDefault(
                    review.getId(), List.of());
            long passed = snapshots.stream()
                    .filter(s -> Constants.ReviewMark.PASS.equals(s.getLastMark())).count();
            long pending = snapshots.stream()
                    .filter(s -> s.getLastMark() == null || s.getLastMark().isBlank()).count();
            long total = snapshots.size();
            dto.setTotalAssociated(total);
            dto.setPassed(passed);
            dto.setProgressPercent(total > 0
                    ? Math.round((total - pending) * 10000.0 / total) / 100.0
                    : 0.0);
            dto.setPassRate(total > 0
                    ? Math.round(passed * 10000.0 / total) / 100.0
                    : 0.0);
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestReviewDetailRespDTO createReview(UUID projectId, UUID userId,
            TestReviewCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        // 校验所有参与者是当前工作空间成员
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }
        UUID workspaceId = project.getWorkspaceId();
        for (UUID participantId : reqDTO.getParticipantIds()) {
            WorkspaceUser wu = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, participantId);
            if (wu == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
            }
        }

        TestReview review = TestReviewConvertMapper.INSTANCE.toEntity(reqDTO);
        review.setProjectId(projectId);
        review.setInitiatorId(userId);
        review.setStatus(ReviewStatus.NEW.getCode());
        testReviewMapper.insert(review);

        reviewSnapshotService.generateSnapshots(review.getId(), reqDTO.getSelectedNodes());
        projectActivityService.record(projectId, userId, "TEST_REVIEW", review.getId(),
                review.getTitle(), "REVIEW_CREATED", "创建评审「" + review.getTitle() + "」");

        return convertToDetailDTO(review);
    }

    @Override
    public TestReviewDetailRespDTO getReviewDetail(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        return convertToDetailDTO(review);
    }

    @Override
    public List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(UUID reviewId, UUID documentId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);

        return reviewSnapshotService.getSnapshotTree(reviewId, documentId);
    }

    @Override
    public List<SnapshotModuleTreeRespDTO> getReviewModuleTree(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);

        return reviewSnapshotService.getModuleTree(reviewId);
    }

    @Override
    public List<PlannedCasesRespDTO> getReviewPlannedCases(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);

        return reviewSnapshotService.getPlannedCases(reviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReviewCases(UUID reviewId, UUID userId, TestReviewCasesUpdateReqDTO reqDTO) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        // 已完成的评审不可再调整，待评审/进行中均允许
        reviewWorkflow.assertTransition(review, ReviewEvent.UPDATE_CASES);

        reviewSnapshotService.updateCases(reviewId, review.getProjectId(), reqDTO.getSelectedNodes());
        projectActivityService.record(review.getProjectId(), userId, "TEST_REVIEW", reviewId,
                review.getTitle(), "REVIEW_CASES_UPDATED", "调整评审「" + review.getTitle() + "」的用例");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReviewRecord(UUID reviewId, UUID userId,
            TestReviewRecordReqDTO reqDTO) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        String previousStatus = review.getStatus();
        // 完成后不可再标记；非法跃迁（COMPLETED 状态下提交记录）由状态机统一拦截
        reviewWorkflow.assertTransition(review, ReviewEvent.SUBMIT_RECORD);

        TestReviewNodeSnapshot snapshotNode = reviewSnapshotService.getNode(
                reqDTO.getSnapshotNodeId());
        if (snapshotNode == null || !snapshotNode.getReviewId().equals(reviewId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }

        if (Constants.ReviewOperation.MARK.equals(reqDTO.getOperationType())) {
            if (!Constants.NodeType.CASE.equals(snapshotNode.getType())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ONLY_CASE_NODE_CAN_MARK_REVIEW);
            }
            String mark = reqDTO.getMark();
            // 空值兼容历史调用，与显式 pending 等价：重置回待评审
            boolean isPending = mark == null || mark.isBlank() || Constants.ReviewMark.PENDING.equals(mark);
            if (!isPending && !Constants.ReviewMark.PASS.equals(mark) && !Constants.ReviewMark.FAIL.equals(mark)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (isPending) {
                reviewSnapshotService.resetMarkAsPending(snapshotNode.getId(), userId, LocalDateTime.now());
            } else {
                reviewSnapshotService.applyMark(snapshotNode.getId(), userId, mark, LocalDateTime.now());
            }
            // 需求：标记评审结果后待评审评审自动转入进行中
            ReviewStatus next = reviewWorkflow.transition(review, ReviewEvent.SUBMIT_RECORD);
            if (next == ReviewStatus.IN_PROGRESS && !ReviewStatus.IN_PROGRESS.equals(review.getStatus())) {
                review.setStatus(next.getCode());
                TestReview reviewUpdate = new TestReview();
                reviewUpdate.setId(review.getId());
                reviewUpdate.setStatus(next.getCode());
                testReviewMapper.updateById(reviewUpdate);
            }
        }

        TestReviewRecord record = new TestReviewRecord();
        record.setReviewId(reviewId);
        record.setSnapshotNodeId(reqDTO.getSnapshotNodeId());
        record.setReviewerId(userId);
        record.setOperationType(reqDTO.getOperationType());
        record.setMark(reqDTO.getMark());
        record.setComment(reqDTO.getComment());
        reviewRecordMapper.insert(record);
        if (!Objects.equals(previousStatus, review.getStatus())) {
            projectActivityService.record(review.getProjectId(), userId, "TEST_REVIEW", reviewId,
                    review.getTitle(), "REVIEW_STATUS_CHANGED", "评审「" + review.getTitle() + "」状态更新");
        }
    }

    @Override
    public List<TestReviewRecordRespDTO> getNodeReviewRecords(UUID reviewId, UUID nodeId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        List<TestReviewRecord> records = reviewRecordMapper.listByReviewIdAndNodeId(reviewId, nodeId);

        return records.stream().map(record -> {
            TestReviewRecordRespDTO dto = new TestReviewRecordRespDTO();
            dto.setId(record.getId());
            dto.setSnapshotNodeId(record.getSnapshotNodeId());
            dto.setReviewerId(record.getReviewerId());
            dto.setOperationType(record.getOperationType());
            dto.setMark(record.getMark());
            dto.setComment(record.getComment());
            dto.setCreatedAt(record.getCreatedAt());

            SysUser reviewer = userMapper.selectById(record.getReviewerId());
            if (reviewer != null) {
                dto.setReviewerName(reviewer.getUsername());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeReview(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        // 完成评审只允许发起人；终态幂等（COMPLETED × COMPLETE 返回 COMPLETED）
        reviewWorkflow.assertTransition(review, ReviewEvent.COMPLETE);
        TestReview update = new TestReview();
        update.setId(review.getId());
        update.setStatus(ReviewStatus.COMPLETED.getCode());
        testReviewMapper.updateById(update);
        // 评审离开 in_progress：发布生命周期事件（AI 域消费者在事务提交后取消 review_check 任务）
        eventPublisher.publishEvent(new ReviewLifecycleEvent(reviewId));
        // 评审结论事件：verdict 由评审域按快照确定性判定，AI 域事务提交后生成 review 级结论（06 §5.2）
        ReviewConclusionEvaluator.Conclusion conclusion = ReviewConclusionEvaluator.evaluate(
                reviewSnapshotService.listAssociatedByReviewId(reviewId, Constants.NodeType.CASE));
        eventPublisher.publishEvent(new ReviewConclusionEvent(reviewId,
                conclusion.verdict().getCode(), conclusion.reason()));
        projectActivityService.record(review.getProjectId(), userId, "TEST_REVIEW", reviewId,
                review.getTitle(), "REVIEW_COMPLETED", "完成评审「" + review.getTitle() + "」");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        // 无物理外键，需显式级联删除快照与评审记录
        reviewRecordMapper.deleteByReviewId(reviewId);
        reviewSnapshotService.deleteByReviewId(reviewId);
        testReviewMapper.deleteById(reviewId);
        projectActivityService.record(review.getProjectId(), userId, "TEST_REVIEW", reviewId,
                review.getTitle(), "REVIEW_DELETED", "删除评审「" + review.getTitle() + "」");
        // 评审实体级出口同样发布生命周期事件（事务提交后取消 review_check 任务）
        eventPublisher.publishEvent(new ReviewLifecycleEvent(reviewId));
    }

    @Override
    public TestReviewProgressRespDTO getReviewProgress(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);

        List<TestReviewNodeSnapshot> snapshots = reviewSnapshotService.listAssociatedByReviewId(reviewId, Constants.NodeType.CASE);

        TestReviewProgressRespDTO dto = new TestReviewProgressRespDTO();
        dto.setTotalAssociated(snapshots.size());

        long passed = 0, failed = 0, pending = 0;
        for (TestReviewNodeSnapshot snap : snapshots) {
            String mark = snap.getLastMark();
            if (mark == null || mark.isBlank()) {
                pending++;
            } else if (Constants.ReviewMark.PASS.equals(mark)) {
                passed++;
            } else {
                failed++;
            }
        }
        dto.setPassed(passed);
        dto.setFailed(failed);
        dto.setPending(pending);

        long total = dto.getTotalAssociated();
        dto.setProgressPercent(total > 0
                ? Math.round((total - pending) * 10000.0 / total) / 100.0
                : 0.0);

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncReview(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(review.getProjectId(), userId);
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        // 已完成的评审快照已定格，不再允许同步（非法跃迁由状态机拦截）
        reviewWorkflow.assertTransition(review, ReviewEvent.SYNC);

        reviewSnapshotService.syncSnapshots(reviewId);
    }

    private TestReviewDetailRespDTO convertToDetailDTO(TestReview review) {
        TestReviewDetailRespDTO dto = TestReviewConvertMapper.INSTANCE.toDetailDTO(review);

        SysUser initiator = userMapper.selectById(review.getInitiatorId());
        if (initiator != null) {
            TestReviewDetailRespDTO.InitiatorInfo info = new TestReviewDetailRespDTO.InitiatorInfo();
            info.setId(initiator.getId());
            info.setName(initiator.getUsername());
            dto.setInitiator(info);
        }
        return dto;
    }
}
