package io.github.xiaomisum.robotest.service.project;

import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestReviewConvertMapper;
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
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.project.TestReviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
    @Resource
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Resource
    private TestReviewRecordMapper reviewRecordMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private SysUserMapper userMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    @Override
    public PageResult<TestReviewListRespDTO> getReviewPage(UUID projectId, String status,
            String keyword, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapperX<TestReview> wrapper = new LambdaQueryWrapperX<TestReview>()
                .eq(TestReview::getProjectId, projectId)
                // 标题关键字模糊匹配，空值自动忽略
                .likeIfPresent(TestReview::getTitle, keyword);
        if (StringUtils.hasText(status)) {
            wrapper.eq(TestReview::getStatus, status);
        }
        wrapper.orderByDesc(TestReview::getCreatedAt);

        PageResult<TestReview> page = testReviewMapper.selectPage(
                new PageParam() {
                    {
                        setPageNo(pageNo);
                        setPageSize(pageSize);
                    }
                }, wrapper);

        // 列表展示进度/通过率：批量查本页全部关联用例快照，避免逐行 N+1
        List<UUID> reviewIds = page.getList().stream().map(TestReview::getId).toList();
        Map<UUID, List<TestReviewNodeSnapshot>> snapshotsByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewNodeSnapshotMapper.selectList(
                        new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                                .in(TestReviewNodeSnapshot::getReviewId, reviewIds)
                                .eq(TestReviewNodeSnapshot::getIsAssociated, true)
                                .eq(TestReviewNodeSnapshot::getType, Constants.NodeType.CASE))
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

        TestReview review = new TestReview();
        review.setProjectId(projectId);
        review.setTitle(reqDTO.getTitle());
        review.setDescription(reqDTO.getDescription());
        review.setInitiatorId(userId.toString());
        review.setParticipantIds(reqDTO.getParticipantIds());
        // 需求：新建评审默认待评审，首次标记时才自动转入进行中
        review.setStatus(Constants.Status.NEW);
        testReviewMapper.insert(review);

        generateSnapshots(review.getId(), reqDTO.getSelectedNodes());

        return convertToDetailDTO(review);
    }

    @Override
    public TestReviewDetailRespDTO getReviewDetail(UUID reviewId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        return convertToDetailDTO(review);
    }

    @Override
    public List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(UUID reviewId, UUID documentId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }

        LambdaQueryWrapperX<TestReviewNodeSnapshot> wrapper = new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId);
        if (documentId != null) {
            wrapper.eq(TestReviewNodeSnapshot::getDocumentSnapshotId, documentId);
        }

        List<TestReviewNodeSnapshot> allNodes = reviewNodeSnapshotMapper.selectList(wrapper);
        List<TestReviewSnapshotNodeRespDTO> dtos = allNodes.stream()
                .map(this::convertToSnapshotNodeDTO)
                .collect(Collectors.toList());

        return pruneSnapshotTree(dtos);
    }

    @Override
    public List<SnapshotModuleTreeRespDTO> getReviewModuleTree(UUID reviewId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }

        List<TestReviewModuleSnapshot> modules = reviewModuleSnapshotMapper.listByReviewId(reviewId);

        List<SnapshotModuleTreeRespDTO> dtos = modules.stream().map(m -> {
            SnapshotModuleTreeRespDTO dto = new SnapshotModuleTreeRespDTO();
            dto.setId(m.getId());
            dto.setParentId(m.getParentId());
            dto.setName(m.getName());
            dto.setType(m.getType());
            dto.setSortOrder(m.getSortOrder());
            return dto;
        }).collect(Collectors.toList());

        return buildModuleTree(dtos);
    }

    private List<SnapshotModuleTreeRespDTO> buildModuleTree(List<SnapshotModuleTreeRespDTO> nodes) {
        Map<String, List<SnapshotModuleTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));
        List<SnapshotModuleTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillModuleChildren(root, parentMap));
        return roots;
    }

    private void fillModuleChildren(SnapshotModuleTreeRespDTO node,
            Map<String, List<SnapshotModuleTreeRespDTO>> parentMap) {
        List<SnapshotModuleTreeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillModuleChildren(child, parentMap));
    }

    @Override
    public List<PlannedCasesRespDTO> getReviewPlannedCases(UUID reviewId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }

        List<PlannedCasesRespDTO> result = new ArrayList<>();
        for (TestReviewModuleSnapshot docSnap : selectDocumentSnapshots(reviewId)) {
            List<UUID> caseIds = reviewNodeSnapshotMapper.selectList(
                    new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                            .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                            .eq(TestReviewNodeSnapshot::getDocumentSnapshotId, docSnap.getId())
                            .eq(TestReviewNodeSnapshot::getIsAssociated, true))
                    .stream()
                    .map(TestReviewNodeSnapshot::getOriginalNodeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (caseIds.isEmpty()) {
                continue;
            }
            PlannedCasesRespDTO dto = new PlannedCasesRespDTO();
            dto.setDocumentId(docSnap.getOriginalModuleId());
            dto.setCaseIds(caseIds);
            result.add(dto);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReviewCases(UUID reviewId, TestReviewCasesUpdateReqDTO reqDTO) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        // 已完成的评审不可再调整，待评审/进行中均允许
        if (Constants.Status.COMPLETED.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_FINISHED);
        }

        Map<UUID, Set<UUID>> newSelection = new LinkedHashMap<>();
        for (TestReviewCreateReqDTO.SelectedNode sn : reqDTO.getSelectedNodes()) {
            TestCaseModule doc = testCaseModuleMapper.selectById(sn.getDocumentId());
            if (doc == null || !doc.getProjectId().equals(review.getProjectId())
                    || !Constants.ModuleType.DOCUMENT.equals(doc.getType())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
            }
            newSelection.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
        }

        Map<UUID, TestReviewModuleSnapshot> existingDocs = selectDocumentSnapshots(reviewId).stream()
                .collect(Collectors.toMap(TestReviewModuleSnapshot::getOriginalModuleId, m -> m));

        // 1. 移除文档：删节点快照与文档快照（标记/评论记录保留作审计），再清理空目录快照
        for (Map.Entry<UUID, TestReviewModuleSnapshot> entry : existingDocs.entrySet()) {
            if (newSelection.containsKey(entry.getKey())) {
                continue;
            }
            reviewNodeSnapshotMapper.delete(
                    new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                            .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                            .eq(TestReviewNodeSnapshot::getDocumentSnapshotId, entry.getValue().getId()));
            reviewModuleSnapshotMapper.deleteById(entry.getValue().getId());
        }
        pruneEmptyDirectorySnapshots(reviewId);

        // 2. 新增文档：复用创建时的快照生成（内部已预置库中已有模块，不会重复复制目录）
        List<TestReviewCreateReqDTO.SelectedNode> added = reqDTO.getSelectedNodes().stream()
                .filter(sn -> !existingDocs.containsKey(sn.getDocumentId()))
                .collect(Collectors.toList());
        if (!added.isEmpty()) {
            generateSnapshots(reviewId, added);
        }

        // 3. 保留文档：补全快照后新增的节点，并按新选择重刷关联标记
        for (Map.Entry<UUID, Set<UUID>> entry : newSelection.entrySet()) {
            TestReviewModuleSnapshot docSnap = existingDocs.get(entry.getKey());
            if (docSnap != null) {
                refreshDocumentSnapshot(reviewId, docSnap, entry.getValue());
            }
        }
    }

    private List<TestReviewModuleSnapshot> selectDocumentSnapshots(UUID reviewId) {
        return reviewModuleSnapshotMapper.selectList(
                new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                        .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                        .eq(TestReviewModuleSnapshot::getType, Constants.ModuleType.DOCUMENT))
                .stream()
                .filter(m -> m.getOriginalModuleId() != null)
                .collect(Collectors.toList());
    }

    // 移除文档后其祖先目录可能不再挂任何快照，自底向上循环清理，避免快照树残留空目录
    private void pruneEmptyDirectorySnapshots(UUID reviewId) {
        boolean removed = true;
        while (removed) {
            removed = false;
            List<TestReviewModuleSnapshot> all = reviewModuleSnapshotMapper.listByReviewId(reviewId);
            Set<UUID> referencedParents = all.stream()
                    .map(TestReviewModuleSnapshot::getParentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (TestReviewModuleSnapshot snap : all) {
                if (Constants.ModuleType.DIRECTORY.equals(snap.getType())
                        && !referencedParents.contains(snap.getId())) {
                    reviewModuleSnapshotMapper.deleteById(snap.getId());
                    removed = true;
                }
            }
        }
    }

    // 补全快照缺失节点（快照后新建的用例，sync 只更新不新增）并重刷 isAssociated
    private void refreshDocumentSnapshot(UUID reviewId, TestReviewModuleSnapshot docSnap, Set<UUID> caseIds) {
        Map<UUID, TestReviewNodeSnapshot> snapByOriginal = reviewNodeSnapshotMapper.selectList(
                new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                        .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                        .eq(TestReviewNodeSnapshot::getDocumentSnapshotId, docSnap.getId()))
                .stream()
                .filter(s -> s.getOriginalNodeId() != null)
                .collect(Collectors.toMap(TestReviewNodeSnapshot::getOriginalNodeId, s -> s, (a, b) -> a));

        Map<UUID, TestCaseNode> currentById = testCaseNodeMapper.listByDocumentId(docSnap.getOriginalModuleId())
                .stream()
                .collect(Collectors.toMap(TestCaseNode::getId, n -> n));

        for (TestCaseNode node : currentById.values()) {
            ensureNodeSnapshot(reviewId, docSnap.getId(), node, currentById, snapByOriginal, caseIds);
        }

        for (TestReviewNodeSnapshot snap : snapByOriginal.values()) {
            boolean associated = caseIds.contains(snap.getOriginalNodeId());
            if (!Objects.equals(associated, snap.getIsAssociated())) {
                // 仅回写关联标记，避免整行覆盖并发产生的评审结果
                TestReviewNodeSnapshot snapUpdate = new TestReviewNodeSnapshot();
                snapUpdate.setId(snap.getId());
                snapUpdate.setIsAssociated(associated);
                reviewNodeSnapshotMapper.updateById(snapUpdate);
            }
        }
    }

    // 递归保证父链先于子节点入快照（库返回顺序任意，逆序插入会产生父映射落空的孤儿根），返回该节点的快照 ID
    private UUID ensureNodeSnapshot(UUID reviewId, UUID docSnapshotId, TestCaseNode node,
            Map<UUID, TestCaseNode> currentById,
            Map<UUID, TestReviewNodeSnapshot> snapByOriginal,
            Set<UUID> associatedCaseIds) {
        TestReviewNodeSnapshot existing = snapByOriginal.get(node.getId());
        if (existing != null) {
            return existing.getId();
        }
        UUID parentSnapshotId = null;
        if (node.getParentId() != null) {
            TestCaseNode parent = currentById.get(node.getParentId());
            if (parent != null) {
                parentSnapshotId = ensureNodeSnapshot(reviewId, docSnapshotId, parent, currentById, snapByOriginal,
                        associatedCaseIds);
            }
        }
        TestReviewNodeSnapshot snapshot = new TestReviewNodeSnapshot();
        snapshot.setReviewId(reviewId);
        snapshot.setOriginalNodeId(node.getId());
        snapshot.setDocumentSnapshotId(docSnapshotId);
        snapshot.setParentId(parentSnapshotId);
        snapshot.setTitle(node.getTitle());
        snapshot.setType(node.getType());
        snapshot.setPriority(node.getPriority());
        snapshot.setIsAssociated(associatedCaseIds.contains(node.getId()));
        snapshot.setSortOrder(node.getSortOrder());
        reviewNodeSnapshotMapper.insert(snapshot);
        snapByOriginal.put(node.getId(), snapshot);
        return snapshot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReviewRecord(UUID reviewId, UUID userId,
            TestReviewRecordReqDTO reqDTO) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        // 完成后不可再标记；待评审状态首次标记即视为评审开始
        if (Constants.Status.COMPLETED.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_FINISHED);
        }

        TestReviewNodeSnapshot snapshotNode = reviewNodeSnapshotMapper.selectById(
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
                // 待评审即初始态 last_mark = NULL；updateById 会忽略 null 字段，须显式 set
                reviewNodeSnapshotMapper.update(null,
                        new LambdaUpdateWrapperX<TestReviewNodeSnapshot>()
                                .eq(TestReviewNodeSnapshot::getId, snapshotNode.getId())
                                .set(TestReviewNodeSnapshot::getLastMark, null)
                                .set(TestReviewNodeSnapshot::getLastReviewerId, userId)
                                .set(TestReviewNodeSnapshot::getLastReviewedAt, LocalDateTime.now()));
            } else {
                // 更新载体只携带本次标记字段，避免全列覆盖导致并发丢失更新
                TestReviewNodeSnapshot snapUpdate = new TestReviewNodeSnapshot();
                snapUpdate.setId(snapshotNode.getId());
                snapUpdate.setLastMark(mark);
                snapUpdate.setLastReviewerId(userId);
                snapUpdate.setLastReviewedAt(LocalDateTime.now());
                reviewNodeSnapshotMapper.updateById(snapUpdate);
            }
            // 需求：标记评审结果后待评审评审自动转入进行中
            if (Constants.Status.NEW.equals(review.getStatus())) {
                review.setStatus(Constants.Status.IN_PROGRESS);
                TestReview reviewUpdate = new TestReview();
                reviewUpdate.setId(review.getId());
                reviewUpdate.setStatus(Constants.Status.IN_PROGRESS);
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
    }

    @Override
    public List<TestReviewRecordRespDTO> getNodeReviewRecords(UUID reviewId, UUID nodeId) {
        List<TestReviewRecord> records = reviewRecordMapper.selectList(
                new LambdaQueryWrapperX<TestReviewRecord>()
                        .eq(TestReviewRecord::getReviewId, reviewId)
                        .eq(TestReviewRecord::getSnapshotNodeId, nodeId)
                        .orderByAsc(TestReviewRecord::getCreatedAt));

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
        if (!review.getInitiatorId().equals(userId.toString())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        TestReview update = new TestReview();
        update.setId(review.getId());
        update.setStatus(Constants.Status.COMPLETED);
        testReviewMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(UUID reviewId, UUID userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId.toString())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        // 无物理外键，需显式级联删除快照与评审记录
        reviewRecordMapper.delete(new LambdaQueryWrapperX<TestReviewRecord>()
                .eq(TestReviewRecord::getReviewId, reviewId));
        reviewNodeSnapshotMapper.delete(new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId));
        reviewModuleSnapshotMapper.delete(new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                .eq(TestReviewModuleSnapshot::getReviewId, reviewId));
        testReviewMapper.deleteById(reviewId);
    }

    @Override
    public TestReviewProgressRespDTO getReviewProgress(UUID reviewId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }

        List<TestReviewNodeSnapshot> snapshots = reviewNodeSnapshotMapper.selectList(
                new LambdaQueryWrapperX<TestReviewNodeSnapshot>()
                        .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                        .eq(TestReviewNodeSnapshot::getIsAssociated, true)
                        .eq(TestReviewNodeSnapshot::getType, Constants.NodeType.CASE));

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
        if (!review.getInitiatorId().equals(userId.toString())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        // 已完成的评审快照已定格，不再允许同步
        if (Constants.Status.COMPLETED.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_FINISHED);
        }

        List<TestReviewNodeSnapshot> snapshotNodes = reviewNodeSnapshotMapper.listByReviewId(reviewId);

        // 1. 同步模块快照：名称、排序与原始模块保持一致；已删除的模块移除快照
        List<TestReviewModuleSnapshot> snapshotModules = reviewModuleSnapshotMapper.listByReviewId(reviewId);

        Set<UUID> validModuleSnapshotIds = new HashSet<>();
        for (TestReviewModuleSnapshot moduleSnap : snapshotModules) {
            if (moduleSnap.getOriginalModuleId() == null) {
                validModuleSnapshotIds.add(moduleSnap.getId());
                continue;
            }
            TestCaseModule originalModule = testCaseModuleMapper.selectById(moduleSnap.getOriginalModuleId());
            if (originalModule == null || originalModule.getIsDeleted()) {
                // 原始模块已删除，移除对应的模块快照和节点快照
                reviewModuleSnapshotMapper.deleteById(moduleSnap.getId());
                for (TestReviewNodeSnapshot nodeSnap : snapshotNodes) {
                    if (moduleSnap.getId().equals(nodeSnap.getDocumentSnapshotId())) {
                        reviewNodeSnapshotMapper.deleteById(nodeSnap.getId());
                    }
                }
            } else {
                // 原始模块仍存在，同步名称和排序；载体只携带同步字段，避免整行覆盖并发变更
                TestReviewModuleSnapshot moduleUpdate = new TestReviewModuleSnapshot();
                moduleUpdate.setId(moduleSnap.getId());
                moduleUpdate.setName(originalModule.getName());
                moduleUpdate.setSortOrder(originalModule.getSortOrder());
                reviewModuleSnapshotMapper.updateById(moduleUpdate);
                validModuleSnapshotIds.add(moduleSnap.getId());
            }
        }

        // 2. 同步节点快照：标题、类型、优先级、排序与原始节点保持一致；已删除的节点标记 isDeleted
        for (TestReviewNodeSnapshot snapshot : snapshotNodes) {
            if (snapshot.getOriginalNodeId() == null) {
                continue;
            }
            // 如果所属模块快照已被删除，跳过
            if (snapshot.getDocumentSnapshotId() != null
                    && !validModuleSnapshotIds.contains(snapshot.getDocumentSnapshotId())) {
                continue;
            }
            TestCaseNode currentNode = testCaseNodeMapper.selectById(snapshot.getOriginalNodeId());
            // 载体只携带同步字段，避免整行覆盖并发写入的评审结果
            TestReviewNodeSnapshot nodeUpdate = new TestReviewNodeSnapshot();
            nodeUpdate.setId(snapshot.getId());
            if (currentNode == null || currentNode.getIsDeleted()) {
                nodeUpdate.setIsDeleted(true);
            } else {
                nodeUpdate.setTitle(currentNode.getTitle());
                nodeUpdate.setType(currentNode.getType());
                nodeUpdate.setPriority(currentNode.getPriority());
                nodeUpdate.setSortOrder(currentNode.getSortOrder());
            }
            reviewNodeSnapshotMapper.updateById(nodeUpdate);
        }
    }

    private void generateSnapshots(UUID reviewId, List<TestReviewCreateReqDTO.SelectedNode> selectedNodes) {
        Map<UUID, Set<UUID>> docCaseMap = new LinkedHashMap<>();
        for (TestReviewCreateReqDTO.SelectedNode sn : selectedNodes) {
            docCaseMap.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
        }

        Set<UUID> copiedModuleIds = reviewModuleSnapshotMapper.listByReviewId(reviewId)
                .stream()
                .map(TestReviewModuleSnapshot::getOriginalModuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (Map.Entry<UUID, Set<UUID>> entry : docCaseMap.entrySet()) {
            UUID documentId = entry.getKey();

            List<UUID> modulePath = getModulePath(documentId);
            for (UUID moduleId : modulePath) {
                if (copiedModuleIds.contains(moduleId)) {
                    continue;
                }
                copiedModuleIds.add(moduleId);

                TestCaseModule original = testCaseModuleMapper.selectById(moduleId);
                if (original == null) {
                    continue;
                }
                TestReviewModuleSnapshot snapshot = new TestReviewModuleSnapshot();
                snapshot.setReviewId(reviewId);
                snapshot.setOriginalModuleId(original.getId());
                snapshot.setParentId(findCopiedParentId(original.getParentId(), copiedModuleIds, reviewId));
                snapshot.setName(original.getName());
                snapshot.setType(original.getType());
                snapshot.setSortOrder(original.getSortOrder());
                reviewModuleSnapshotMapper.insert(snapshot);
            }

            List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(documentId);

            UUID snapshotDocId = findSnapshotModuleId(documentId, reviewId);
            Set<UUID> caseIds = entry.getValue();

            // 递归插入保证父先于子，避免库返回顺序导致父映射落空
            Map<UUID, TestCaseNode> currentById = docNodes.stream()
                    .collect(Collectors.toMap(TestCaseNode::getId, n -> n));
            Map<UUID, TestReviewNodeSnapshot> snapByOriginal = new HashMap<>();
            for (TestCaseNode node : docNodes) {
                ensureNodeSnapshot(reviewId, snapshotDocId, node, currentById, snapByOriginal, caseIds);
            }
        }
    }

    private List<UUID> getModulePath(UUID documentId) {
        List<UUID> path = new ArrayList<>();
        UUID currentId = documentId;
        while (currentId != null) {
            path.add(0, currentId);
            TestCaseModule module = testCaseModuleMapper.selectById(currentId);
            if (module == null) {
                break;
            }
            currentId = module.getParentId();
        }
        return path;
    }

    private UUID findCopiedParentId(UUID originalParentId, Set<UUID> copiedModuleIds, UUID reviewId) {
        if (originalParentId == null) {
            return null;
        }
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                        .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                        .eq(TestReviewModuleSnapshot::getOriginalModuleId, originalParentId));
        return snapshot != null ? snapshot.getId() : null;
    }

    private UUID findSnapshotModuleId(UUID originalModuleId, UUID reviewId) {
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapperX<TestReviewModuleSnapshot>()
                        .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                        .eq(TestReviewModuleSnapshot::getOriginalModuleId, originalModuleId));
        return snapshot != null ? snapshot.getId() : null;
    }

    private List<TestReviewSnapshotNodeRespDTO> pruneSnapshotTree(
            List<TestReviewSnapshotNodeRespDTO> allNodes) {

        Set<UUID> associatedIds = allNodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsAssociated()))
                .map(TestReviewSnapshotNodeRespDTO::getId)
                .collect(Collectors.toSet());

        Map<UUID, TestReviewSnapshotNodeRespDTO> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(
                        TestReviewSnapshotNodeRespDTO::getId, n -> n));

        Set<UUID> keepIds = new HashSet<>(associatedIds);

        for (UUID assocId : associatedIds) {
            UUID parentId = nodeMap.get(assocId) != null ? nodeMap.get(assocId).getParentId() : null;
            while (parentId != null) {
                keepIds.add(parentId);
                TestReviewSnapshotNodeRespDTO parentNode = nodeMap.get(parentId);
                parentId = parentNode != null ? parentNode.getParentId() : null;
            }
        }

        for (UUID assocId : associatedIds) {
            collectDescendants(assocId, nodeMap, keepIds);
        }

        List<TestReviewSnapshotNodeRespDTO> filtered = allNodes.stream()
                .filter(n -> keepIds.contains(n.getId()))
                .collect(Collectors.toList());

        return buildSnapshotTree(filtered);
    }

    private void collectDescendants(UUID nodeId, Map<UUID, TestReviewSnapshotNodeRespDTO> nodeMap,
            Set<UUID> keepIds) {
        for (TestReviewSnapshotNodeRespDTO node : nodeMap.values()) {
            if (nodeId.equals(node.getParentId())) {
                keepIds.add(node.getId());
                collectDescendants(node.getId(), nodeMap, keepIds);
            }
        }
    }

    private List<TestReviewSnapshotNodeRespDTO> buildSnapshotTree(
            List<TestReviewSnapshotNodeRespDTO> nodes) {
        Map<UUID, List<TestReviewSnapshotNodeRespDTO>> parentMap = nodes.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(TestReviewSnapshotNodeRespDTO::getParentId));

        List<TestReviewSnapshotNodeRespDTO> roots = nodes.stream()
                .filter(n -> n.getParentId() == null)
                .collect(Collectors.toList());
        roots.forEach(root -> fillSnapshotChildren(root, parentMap));
        return roots;
    }

    private void fillSnapshotChildren(TestReviewSnapshotNodeRespDTO node,
            Map<UUID, List<TestReviewSnapshotNodeRespDTO>> parentMap) {
        List<TestReviewSnapshotNodeRespDTO> children = parentMap.getOrDefault(node.getId(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillSnapshotChildren(child, parentMap));
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

    private TestReviewSnapshotNodeRespDTO convertToSnapshotNodeDTO(TestReviewNodeSnapshot snapshot) {
        return TestReviewConvertMapper.INSTANCE.toSnapshotNodeDTO(snapshot);
    }
}
