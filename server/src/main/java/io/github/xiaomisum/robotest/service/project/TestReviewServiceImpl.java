package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestReviewConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestReviewRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import io.github.xiaomisum.robotest.service.project.TestReviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;
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

    @Override
    public PageResult<TestReviewListRespDTO> getReviewPage(String projectId, String status,
                                                      Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<TestReview> wrapper = new LambdaQueryWrapper<TestReview>()
                .eq(TestReview::getProjectId, projectId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(TestReview::getStatus, status);
        }
        wrapper.orderByDesc(TestReview::getCreatedAt);

        PageResult<TestReview> page = testReviewMapper.selectPage(
                new PageParam() {{ setPageNo(pageNo); setPageSize(pageSize); }}, wrapper);

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
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestReviewDetailRespDTO createReview(String projectId, String userId,
                                                 TestReviewCreateReqDTO reqDTO) {
        TestReview review = new TestReview();
        review.setProjectId(projectId);
        review.setTitle(reqDTO.getTitle());
        review.setDescription(reqDTO.getDescription());
        review.setInitiatorId(userId);
        review.setParticipantIds(reqDTO.getParticipantIds());
        review.setStatus(Constants.Status.IN_PROGRESS);
        testReviewMapper.insert(review);

        generateSnapshots(review.getId().toString(), reqDTO.getSelectedNodes());

        return convertToDetailDTO(review);
    }

    @Override
    public TestReviewDetailRespDTO getReviewDetail(String reviewId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        return convertToDetailDTO(review);
    }

    @Override
    public List<TestReviewSnapshotNodeRespDTO> getReviewSnapshotTree(String reviewId, String documentId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }

        LambdaQueryWrapper<TestReviewNodeSnapshot> wrapper = new LambdaQueryWrapper<TestReviewNodeSnapshot>()
                .eq(TestReviewNodeSnapshot::getReviewId, reviewId);
        if (StringUtils.hasText(documentId)) {
            wrapper.eq(TestReviewNodeSnapshot::getDocumentSnapshotId, documentId);
        }

        List<TestReviewNodeSnapshot> allNodes = reviewNodeSnapshotMapper.selectList(wrapper);
        List<TestReviewSnapshotNodeRespDTO> dtos = allNodes.stream()
                .map(this::convertToSnapshotNodeDTO)
                .collect(Collectors.toList());

        return pruneSnapshotTree(dtos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReviewRecord(String reviewId, String userId,
                                    TestReviewRecordReqDTO reqDTO) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!Constants.Status.IN_PROGRESS.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
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
            if (!Constants.ReviewMark.PASS.equals(reqDTO.getMark()) && !Constants.ReviewMark.FAIL.equals(reqDTO.getMark())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            snapshotNode.setLastMark(reqDTO.getMark());
            snapshotNode.setLastReviewerId(userId);
            snapshotNode.setLastReviewedAt(LocalDateTime.now());
            reviewNodeSnapshotMapper.updateById(snapshotNode);
        }

        TestReviewRecord record = new TestReviewRecord();
        record.setReviewId(reviewId);
        record.setSnapshotNodeId(reqDTO.getSnapshotNodeId().toString());
        record.setReviewerId(userId);
        record.setOperationType(reqDTO.getOperationType());
        record.setMark(reqDTO.getMark());
        record.setComment(reqDTO.getComment());
        reviewRecordMapper.insert(record);
    }

    @Override
    public List<TestReviewRecordRespDTO> getNodeReviewRecords(String reviewId, String nodeId) {
        List<TestReviewRecord> records = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<TestReviewRecord>()
                        .eq(TestReviewRecord::getReviewId, reviewId)
                        .eq(TestReviewRecord::getSnapshotNodeId, nodeId)
                        .orderByAsc(TestReviewRecord::getCreatedAt));

        return records.stream().map(record -> {
            TestReviewRecordRespDTO dto = new TestReviewRecordRespDTO();
            dto.setId(record.getId());
            dto.setSnapshotNodeId(record.getSnapshotNodeId() != null ? UUID.fromString(record.getSnapshotNodeId()) : null);
            dto.setReviewerId(record.getReviewerId() != null ? UUID.fromString(record.getReviewerId()) : null);
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
    public void completeReview(String reviewId, String userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        review.setStatus(Constants.Status.COMPLETED);
        testReviewMapper.updateById(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncReview(String reviewId, String userId) {
        TestReview review = testReviewMapper.selectById(reviewId);
        if (review == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_REVIEW_NOT_FOUND);
        }
        if (!review.getInitiatorId().equals(userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REVIEW_NOT_INITIATOR);
        }
        if (!Constants.Status.IN_PROGRESS.equals(review.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        List<TestReviewNodeSnapshot> snapshotNodes = reviewNodeSnapshotMapper.selectList(
                new LambdaQueryWrapper<TestReviewNodeSnapshot>()
                        .eq(TestReviewNodeSnapshot::getReviewId, reviewId));

        for (TestReviewNodeSnapshot snapshot : snapshotNodes) {
            if (snapshot.getOriginalNodeId() == null) {
                continue;
            }
            TestCaseNode currentNode = testCaseNodeMapper.selectById(snapshot.getOriginalNodeId());
            if (currentNode == null || currentNode.getIsDeleted()) {
                snapshot.setIsDeleted(true);
            } else {
                snapshot.setTitle(currentNode.getTitle());
                snapshot.setType(currentNode.getType());
                snapshot.setPriority(currentNode.getPriority());
                snapshot.setSortOrder(currentNode.getSortOrder());
            }
            reviewNodeSnapshotMapper.updateById(snapshot);
        }
    }

    private void generateSnapshots(String reviewId, List<TestReviewCreateReqDTO.SelectedNode> selectedNodes) {
        Map<String, Set<String>> docCaseMap = new LinkedHashMap<>();
        for (TestReviewCreateReqDTO.SelectedNode sn : selectedNodes) {
            docCaseMap.put(sn.getDocumentId().toString(), sn.getCaseIds().stream().map(UUID::toString).collect(Collectors.toSet()));
        }

        Set<String> copiedModuleIds = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry : docCaseMap.entrySet()) {
            String documentId = entry.getKey();

            List<String> modulePath = getModulePath(documentId);
            for (String moduleId : modulePath) {
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
                snapshot.setOriginalModuleId(original.getId().toString());
                snapshot.setParentId(findCopiedParentId(original.getParentId(), copiedModuleIds, reviewId));
                snapshot.setName(original.getName());
                snapshot.setType(original.getType());
                snapshot.setSortOrder(original.getSortOrder());
                reviewModuleSnapshotMapper.insert(snapshot);
            }

            List<TestCaseNode> docNodes = testCaseNodeMapper.selectList(
                    new LambdaQueryWrapper<TestCaseNode>()
                            .eq(TestCaseNode::getDocumentId, documentId));

            String snapshotDocId = findSnapshotModuleId(documentId, reviewId);
            Set<String> caseIds = entry.getValue();

            for (TestCaseNode node : docNodes) {
                TestReviewNodeSnapshot nodeSnapshot = new TestReviewNodeSnapshot();
                nodeSnapshot.setReviewId(reviewId);
                nodeSnapshot.setOriginalNodeId(node.getId().toString());
                nodeSnapshot.setDocumentSnapshotId(snapshotDocId);
                nodeSnapshot.setParentId(findCopiedNodeParentId(node.getParentId(), reviewId));
                nodeSnapshot.setTitle(node.getTitle());
                nodeSnapshot.setType(node.getType());
                nodeSnapshot.setPriority(node.getPriority());
                nodeSnapshot.setIsAssociated(caseIds.contains(node.getId().toString()));
                nodeSnapshot.setSortOrder(node.getSortOrder());
                reviewNodeSnapshotMapper.insert(nodeSnapshot);
            }
        }
    }

    private List<String> getModulePath(String documentId) {
        List<String> path = new ArrayList<>();
        String currentId = documentId;
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

    private String findCopiedParentId(String originalParentId, Set<String> copiedModuleIds, String reviewId) {
        if (originalParentId == null) {
            return null;
        }
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestReviewModuleSnapshot>()
                        .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                        .eq(TestReviewModuleSnapshot::getOriginalModuleId, originalParentId));
        return snapshot != null ? snapshot.getId().toString() : null;
    }

    private String findSnapshotModuleId(String originalModuleId, String reviewId) {
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestReviewModuleSnapshot>()
                        .eq(TestReviewModuleSnapshot::getReviewId, reviewId)
                        .eq(TestReviewModuleSnapshot::getOriginalModuleId, originalModuleId));
        return snapshot != null ? snapshot.getId().toString() : null;
    }

    private String findCopiedNodeParentId(String originalParentId, String reviewId) {
        if (originalParentId == null) {
            return null;
        }
        TestReviewNodeSnapshot snapshot = reviewNodeSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestReviewNodeSnapshot>()
                        .eq(TestReviewNodeSnapshot::getReviewId, reviewId)
                        .eq(TestReviewNodeSnapshot::getOriginalNodeId, originalParentId));
        return snapshot != null ? snapshot.getId().toString() : null;
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
