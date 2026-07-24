package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestPlanConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import io.github.xiaomisum.robotest.service.project.TestPlanService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TestPlanServiceImpl implements TestPlanService {

    private static final Logger log = LoggerFactory.getLogger(TestPlanServiceImpl.class);

    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private TestPlanModuleSnapshotMapper planModuleSnapshotMapper;
    @Resource
    private TestPlanNodeSnapshotMapper planNodeSnapshotMapper;
    @Resource
    private TestPlanExecutionRecordMapper planExecutionRecordMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private SysUserMapper userMapper;

    @Override
    public PageResult<TestPlanListRespDTO> getPlanPage(String projectId, String status,
                                                  Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<TestPlan> wrapper = new LambdaQueryWrapper<TestPlan>()
                .eq(TestPlan::getProjectId, UUID.fromString(projectId));
        if (StringUtils.hasText(status)) {
            wrapper.eq(TestPlan::getStatus, status);
        }
        wrapper.orderByDesc(TestPlan::getCreatedAt);

        PageResult<TestPlan> page = testPlanMapper.selectPage(
                new PageParam() {{ setPageNo(pageNo); setPageSize(pageSize); }}, wrapper);

        List<TestPlanListRespDTO> dtos = page.getList().stream().map(plan -> {
            TestPlanListRespDTO dto = new TestPlanListRespDTO();
            dto.setId(plan.getId());
            dto.setName(plan.getName());
            dto.setStatus(plan.getStatus());
            dto.setEnvironment(plan.getEnvironment());
            dto.setStartTime(plan.getStartTime());
            dto.setEndTime(plan.getEndTime());
            dto.setCreatedAt(plan.getCreatedAt());

            if (plan.getExecutorId() != null) {
                SysUser executor = userMapper.selectById(plan.getExecutorId());
                if (executor != null) {
                    TestPlanListRespDTO.ExecutorInfo info = new TestPlanListRespDTO.ExecutorInfo();
                    info.setId(executor.getId());
                    info.setName(executor.getUsername());
                    dto.setExecutor(info);
                }
            }
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestPlanDetailRespDTO createPlan(String projectId, UUID userId,
                                             TestPlanCreateReqDTO reqDTO) {
        TestPlan plan = new TestPlan();
        plan.setProjectId(UUID.fromString(projectId));
        plan.setName(reqDTO.getName());
        plan.setDescription(reqDTO.getDescription());
        plan.setStatus(Constants.Status.NEW);
        plan.setExecutorId(reqDTO.getExecutorId());
        plan.setStartTime(reqDTO.getStartTime());
        plan.setEndTime(reqDTO.getEndTime());
        plan.setEnvironment(reqDTO.getEnvironment());
        testPlanMapper.insert(plan);

        generateSnapshots(plan.getId(), reqDTO.getSelectedNodes());

        return convertToDetailDTO(plan);
    }

    @Override
    public TestPlanDetailRespDTO getPlanDetail(UUID planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        return convertToDetailDTO(plan);
    }

    @Override
    public List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(UUID planId, UUID documentId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        LambdaQueryWrapper<TestPlanNodeSnapshot> wrapper = new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId);
        if (documentId != null) {
            wrapper.eq(TestPlanNodeSnapshot::getDocumentSnapshotId, documentId);
        }

        List<TestPlanNodeSnapshot> allNodes = planNodeSnapshotMapper.selectList(wrapper);
        List<TestPlanSnapshotNodeRespDTO> dtos = allNodes.stream()
                .map(this::convertToSnapshotNodeDTO)
                .collect(Collectors.toList());

        return pruneSnapshotTree(dtos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExecutionRecord(UUID planId, UUID userId,
                                       TestPlanRecordReqDTO reqDTO) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!Constants.Status.IN_PROGRESS.equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        TestPlanNodeSnapshot snapshotNode = planNodeSnapshotMapper.selectById(
                reqDTO.getSnapshotNodeId());
        if (snapshotNode == null || !snapshotNode.getPlanId().equals(planId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(snapshotNode.getIsAssociated())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN);
        }
        if (!Constants.NodeType.CASE.equals(snapshotNode.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN);
        }

        snapshotNode.setLastResult(reqDTO.getResult());
        snapshotNode.setLastExecutorId(userId);
        snapshotNode.setLastExecutedAt(LocalDateTime.now());
        planNodeSnapshotMapper.updateById(snapshotNode);

        TestPlanExecutionRecord record = new TestPlanExecutionRecord();
        record.setPlanId(planId);
        record.setSnapshotNodeId(reqDTO.getSnapshotNodeId());
        record.setExecutorId(userId);
        record.setResult(reqDTO.getResult());
        record.setNote(reqDTO.getNote());
        record.setExecutedAt(LocalDateTime.now());
        planExecutionRecordMapper.insert(record);
    }

    @Override
    public List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(UUID planId, UUID nodeId) {
        List<TestPlanExecutionRecord> records = planExecutionRecordMapper.selectList(
                new LambdaQueryWrapper<TestPlanExecutionRecord>()
                        .eq(TestPlanExecutionRecord::getPlanId, planId)
                        .eq(TestPlanExecutionRecord::getSnapshotNodeId, nodeId)
                        .orderByAsc(TestPlanExecutionRecord::getExecutedAt));

        return records.stream().map(record -> {
            TestPlanExecutionRecordRespDTO dto = new TestPlanExecutionRecordRespDTO();
            dto.setId(record.getId());
            dto.setSnapshotNodeId(record.getSnapshotNodeId());
            dto.setExecutorId(record.getExecutorId());
            dto.setResult(record.getResult());
            dto.setNote(record.getNote());
            dto.setExecutedAt(record.getExecutedAt());
            dto.setCreatedAt(record.getCreatedAt());

            SysUser executor = userMapper.selectById(record.getExecutorId());
            if (executor != null) {
                dto.setExecutorName(executor.getUsername());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPlan(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        if (!Constants.Status.IN_PROGRESS.equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        List<TestPlanNodeSnapshot> snapshotNodes = planNodeSnapshotMapper.selectList(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId));

        // 1. 同步模块快照：名称、排序与原始模块保持一致；已删除的模块移除快照
        List<TestPlanModuleSnapshot> snapshotModules = planModuleSnapshotMapper.selectList(
                new LambdaQueryWrapper<TestPlanModuleSnapshot>()
                        .eq(TestPlanModuleSnapshot::getPlanId, planId));

        Set<UUID> validModuleSnapshotIds = new HashSet<>();
        for (TestPlanModuleSnapshot moduleSnap : snapshotModules) {
            if (moduleSnap.getOriginalModuleId() == null) {
                validModuleSnapshotIds.add(moduleSnap.getId());
                continue;
            }
            TestCaseModule originalModule = testCaseModuleMapper.selectById(moduleSnap.getOriginalModuleId());
            if (originalModule == null || originalModule.getIsDeleted()) {
                // 原始模块已删除，移除对应的模块快照和节点快照
                planModuleSnapshotMapper.deleteById(moduleSnap.getId());
                // 移除属于该模块快照的节点快照
                for (TestPlanNodeSnapshot nodeSnap : snapshotNodes) {
                    if (moduleSnap.getId().equals(nodeSnap.getDocumentSnapshotId())) {
                        planNodeSnapshotMapper.deleteById(nodeSnap.getId());
                    }
                }
            } else {
                // 原始模块仍存在，同步名称和排序
                moduleSnap.setName(originalModule.getName());
                moduleSnap.setSortOrder(originalModule.getSortOrder());
                planModuleSnapshotMapper.updateById(moduleSnap);
                validModuleSnapshotIds.add(moduleSnap.getId());
            }
        }

        // 2. 同步节点快照：标题、类型、优先级、排序与原始节点保持一致；已删除的节点标记 isDeleted
        for (TestPlanNodeSnapshot snapshot : snapshotNodes) {
            if (snapshot.getOriginalNodeId() == null) {
                continue;
            }
            // 如果所属模块快照已被删除，跳过
            if (snapshot.getDocumentSnapshotId() != null
                    && !validModuleSnapshotIds.contains(snapshot.getDocumentSnapshotId())) {
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
            planNodeSnapshotMapper.updateById(snapshot);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startPlan(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        if (!Constants.Status.NEW.equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        plan.setStatus(Constants.Status.IN_PROGRESS);
        testPlanMapper.updateById(plan);
    }

    @Override
    public TestPlanProgressRespDTO getPlanProgress(UUID planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        List<TestPlanNodeSnapshot> snapshots = planNodeSnapshotMapper.selectList(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId)
                        .eq(TestPlanNodeSnapshot::getIsAssociated, true)
                        .eq(TestPlanNodeSnapshot::getType, Constants.NodeType.CASE));

        TestPlanProgressRespDTO dto = new TestPlanProgressRespDTO();
        dto.setTotalAssociated(snapshots.size());

        long passed = 0, failed = 0, blocked = 0, untested = 0;
        for (TestPlanNodeSnapshot snap : snapshots) {
            String result = snap.getLastResult();
            if (result == null || Constants.Status.UNTESTED.equals(result)) {
                untested++;
            } else {
                switch (result) {
                    case "pass" -> passed++;
                    case "fail" -> failed++;
                    case "blocked" -> blocked++;
                    default -> untested++;
                }
            }
        }
        dto.setPassed(passed);
        dto.setFailed(failed);
        dto.setBlocked(blocked);
        dto.setUntested(untested);

        long total = dto.getTotalAssociated();
        dto.setProgressPercent(total > 0
                ? Math.round((total - untested) * 10000.0 / total) / 100.0
                : 0.0);

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePlan(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }

        Long untestedCount = planNodeSnapshotMapper.selectCount(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId)
                        .eq(TestPlanNodeSnapshot::getIsAssociated, true)
                        .eq(TestPlanNodeSnapshot::getLastResult, Constants.Status.UNTESTED));
        if (untestedCount > 0) {
            log.warn("Plan {} closed with {} untested associated cases", planId, untestedCount);
        }

        plan.setStatus(Constants.Status.CLOSED);
        testPlanMapper.updateById(plan);
    }

    private void generateSnapshots(UUID planId, List<TestPlanCreateReqDTO.SelectedNode> selectedNodes) {
        Map<UUID, Set<UUID>> docCaseMap = new LinkedHashMap<>();
        for (TestPlanCreateReqDTO.SelectedNode sn : selectedNodes) {
            docCaseMap.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
        }

        Set<UUID> copiedModuleIds = new HashSet<>();

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
                TestPlanModuleSnapshot snapshot = new TestPlanModuleSnapshot();
                snapshot.setPlanId(planId);
                snapshot.setOriginalModuleId(original.getId());
                snapshot.setParentId(findCopiedModuleParentId(original.getParentId(), planId));
                snapshot.setName(original.getName());
                snapshot.setType(original.getType());
                snapshot.setSortOrder(original.getSortOrder());
                planModuleSnapshotMapper.insert(snapshot);
            }

            List<TestCaseNode> docNodes = testCaseNodeMapper.selectList(
                    new LambdaQueryWrapper<TestCaseNode>()
                            .eq(TestCaseNode::getDocumentId, documentId));

            UUID snapshotDocId = findSnapshotModuleId(documentId, planId);
            Set<UUID> caseIds = entry.getValue();

            for (TestCaseNode node : docNodes) {
                TestPlanNodeSnapshot nodeSnapshot = new TestPlanNodeSnapshot();
                nodeSnapshot.setPlanId(planId);
                nodeSnapshot.setOriginalNodeId(node.getId());
                nodeSnapshot.setDocumentSnapshotId(snapshotDocId);
                nodeSnapshot.setParentId(findCopiedNodeParentId(node.getParentId(), planId));
                nodeSnapshot.setTitle(node.getTitle());
                nodeSnapshot.setType(node.getType());
                nodeSnapshot.setPriority(node.getPriority());
                nodeSnapshot.setIsAssociated(caseIds.contains(node.getId()));
                nodeSnapshot.setLastResult(Constants.Status.UNTESTED);
                nodeSnapshot.setSortOrder(node.getSortOrder());
                planNodeSnapshotMapper.insert(nodeSnapshot);
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

    private UUID findCopiedModuleParentId(UUID originalParentId, UUID planId) {
        if (originalParentId == null) {
            return null;
        }
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanModuleSnapshot>()
                        .eq(TestPlanModuleSnapshot::getPlanId, planId)
                        .eq(TestPlanModuleSnapshot::getOriginalModuleId, originalParentId));
        return snapshot != null ? snapshot.getId() : null;
    }

    private UUID findSnapshotModuleId(UUID originalModuleId, UUID planId) {
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanModuleSnapshot>()
                        .eq(TestPlanModuleSnapshot::getPlanId, planId)
                        .eq(TestPlanModuleSnapshot::getOriginalModuleId, originalModuleId));
        return snapshot != null ? snapshot.getId() : null;
    }

    private UUID findCopiedNodeParentId(UUID originalParentId, UUID planId) {
        if (originalParentId == null) {
            return null;
        }
        TestPlanNodeSnapshot snapshot = planNodeSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId)
                        .eq(TestPlanNodeSnapshot::getOriginalNodeId, originalParentId));
        return snapshot != null ? snapshot.getId() : null;
    }

    private List<TestPlanSnapshotNodeRespDTO> pruneSnapshotTree(
            List<TestPlanSnapshotNodeRespDTO> allNodes) {

        Set<UUID> associatedIds = allNodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsAssociated()))
                .map(TestPlanSnapshotNodeRespDTO::getId)
                .collect(Collectors.toSet());

        Map<UUID, TestPlanSnapshotNodeRespDTO> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(
                        TestPlanSnapshotNodeRespDTO::getId, n -> n));

        Set<UUID> keepIds = new HashSet<>(associatedIds);

        for (UUID assocId : associatedIds) {
            UUID parentId = nodeMap.get(assocId) != null ? nodeMap.get(assocId).getParentId() : null;
            while (parentId != null) {
                keepIds.add(parentId);
                TestPlanSnapshotNodeRespDTO parentNode = nodeMap.get(parentId);
                parentId = parentNode != null ? parentNode.getParentId() : null;
            }
        }

        for (UUID assocId : associatedIds) {
            collectDescendants(assocId, nodeMap, keepIds);
        }

        List<TestPlanSnapshotNodeRespDTO> filtered = allNodes.stream()
                .filter(n -> keepIds.contains(n.getId()))
                .collect(Collectors.toList());

        return buildSnapshotTree(filtered);
    }

    private void collectDescendants(UUID nodeId, Map<UUID, TestPlanSnapshotNodeRespDTO> nodeMap,
                                     Set<UUID> keepIds) {
        for (TestPlanSnapshotNodeRespDTO node : nodeMap.values()) {
            if (nodeId.equals(node.getParentId())) {
                keepIds.add(node.getId());
                collectDescendants(node.getId(), nodeMap, keepIds);
            }
        }
    }

    private List<TestPlanSnapshotNodeRespDTO> buildSnapshotTree(
            List<TestPlanSnapshotNodeRespDTO> nodes) {
        Map<String, List<TestPlanSnapshotNodeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<TestPlanSnapshotNodeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillSnapshotChildren(root, parentMap));
        return roots;
    }

    private void fillSnapshotChildren(TestPlanSnapshotNodeRespDTO node,
                                       Map<String, List<TestPlanSnapshotNodeRespDTO>> parentMap) {
        List<TestPlanSnapshotNodeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillSnapshotChildren(child, parentMap));
    }

    private TestPlanDetailRespDTO convertToDetailDTO(TestPlan plan) {
        TestPlanDetailRespDTO dto = TestPlanConvertMapper.INSTANCE.toDetailDTO(plan);

        if (plan.getExecutorId() != null) {
            SysUser executor = userMapper.selectById(plan.getExecutorId());
            if (executor != null) {
                TestPlanDetailRespDTO.ExecutorInfo info = new TestPlanDetailRespDTO.ExecutorInfo();
                info.setId(executor.getId());
                info.setName(executor.getUsername());
                dto.setExecutor(info);
            }
        }
        return dto;
    }

    private TestPlanSnapshotNodeRespDTO convertToSnapshotNodeDTO(TestPlanNodeSnapshot snapshot) {
        return TestPlanConvertMapper.INSTANCE.toSnapshotNodeDTO(snapshot);
    }
}
