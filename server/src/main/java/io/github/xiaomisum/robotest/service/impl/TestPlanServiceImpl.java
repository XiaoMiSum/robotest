package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.*;
import io.github.xiaomisum.robotest.model.entity.*;
import io.github.xiaomisum.robotest.repository.*;
import io.github.xiaomisum.robotest.service.TestPlanService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
                .eq(TestPlan::getProjectId, projectId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(TestPlan::getStatus, status);
        }
        wrapper.orderByDesc(TestPlan::getCreatedAt);

        PageResult<TestPlan> page = testPlanMapper.selectPage(
                new PageParam() {{ setPageNo(pageNo); setPageSize(pageSize); }}, wrapper);

        List<TestPlanListRespDTO> dtos = page.getList().stream().map(plan -> {
            TestPlanListRespDTO dto = new TestPlanListRespDTO();
            dto.setId(plan.getId().toString());
            dto.setName(plan.getName());
            dto.setStatus(plan.getStatus());
            dto.setEnvironment(plan.getEnvironment());
            dto.setStartTime(plan.getStartTime());
            dto.setEndTime(plan.getEndTime());
            dto.setCreatedAt(plan.getCreatedAt());

            if (StringUtils.hasText(plan.getExecutorId())) {
                SysUser executor = userMapper.selectById(plan.getExecutorId());
                if (executor != null) {
                    TestPlanListRespDTO.ExecutorInfo info = new TestPlanListRespDTO.ExecutorInfo();
                    info.setId(executor.getId().toString());
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
    public TestPlanDetailRespDTO createPlan(String projectId, String userId,
                                             TestPlanCreateReqDTO reqDTO) {
        TestPlan plan = new TestPlan();
        plan.setId(UUID.randomUUID());
        plan.setProjectId(projectId);
        plan.setName(reqDTO.getName());
        plan.setDescription(reqDTO.getDescription());
        plan.setStatus("new");
        plan.setExecutorId(reqDTO.getExecutorId());
        plan.setStartTime(reqDTO.getStartTime());
        plan.setEndTime(reqDTO.getEndTime());
        plan.setEnvironment(reqDTO.getEnvironment());
        testPlanMapper.insert(plan);

        generateSnapshots(plan.getId().toString(), reqDTO.getSelectedNodes());

        return convertToDetailDTO(plan);
    }

    @Override
    public TestPlanDetailRespDTO getPlanDetail(String planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        return convertToDetailDTO(plan);
    }

    @Override
    public List<TestPlanSnapshotNodeRespDTO> getPlanSnapshotTree(String planId, String documentId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        LambdaQueryWrapper<TestPlanNodeSnapshot> wrapper = new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                .eq(TestPlanNodeSnapshot::getPlanId, planId);
        if (StringUtils.hasText(documentId)) {
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
    public void submitExecutionRecord(String planId, String userId,
                                       TestPlanRecordReqDTO reqDTO) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!"in_progress".equals(plan.getStatus())) {
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
        if (!"case".equals(snapshotNode.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ONLY_ASSOCIATED_CASE_CAN_MARK_PLAN);
        }

        snapshotNode.setLastResult(reqDTO.getResult());
        snapshotNode.setLastExecutorId(userId);
        snapshotNode.setLastExecutedAt(LocalDateTime.now());
        planNodeSnapshotMapper.updateById(snapshotNode);

        TestPlanExecutionRecord record = new TestPlanExecutionRecord();
        record.setId(UUID.randomUUID());
        record.setPlanId(planId);
        record.setSnapshotNodeId(reqDTO.getSnapshotNodeId());
        record.setExecutorId(userId);
        record.setResult(reqDTO.getResult());
        record.setNote(reqDTO.getNote());
        record.setExecutedAt(LocalDateTime.now());
        planExecutionRecordMapper.insert(record);
    }

    @Override
    public List<TestPlanExecutionRecordRespDTO> getNodeExecutionRecords(String planId, String nodeId) {
        List<TestPlanExecutionRecord> records = planExecutionRecordMapper.selectList(
                new LambdaQueryWrapper<TestPlanExecutionRecord>()
                        .eq(TestPlanExecutionRecord::getPlanId, planId)
                        .eq(TestPlanExecutionRecord::getSnapshotNodeId, nodeId)
                        .orderByAsc(TestPlanExecutionRecord::getExecutedAt));

        return records.stream().map(record -> {
            TestPlanExecutionRecordRespDTO dto = new TestPlanExecutionRecordRespDTO();
            dto.setId(record.getId().toString());
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
    public void syncPlan(String planId, String userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        if (!"in_progress".equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        List<TestPlanNodeSnapshot> snapshotNodes = planNodeSnapshotMapper.selectList(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId));

        for (TestPlanNodeSnapshot snapshot : snapshotNodes) {
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
            planNodeSnapshotMapper.updateById(snapshot);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePlan(String planId, String userId) {
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
                        .eq(TestPlanNodeSnapshot::getLastResult, "untested"));
        if (untestedCount > 0) {
            log.warn("Plan {} closed with {} untested associated cases", planId, untestedCount);
        }

        plan.setStatus("closed");
        testPlanMapper.updateById(plan);
    }

    private void generateSnapshots(String planId, List<TestPlanCreateReqDTO.SelectedNode> selectedNodes) {
        Map<String, Set<String>> docCaseMap = new LinkedHashMap<>();
        for (TestPlanCreateReqDTO.SelectedNode sn : selectedNodes) {
            docCaseMap.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
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
                TestPlanModuleSnapshot snapshot = new TestPlanModuleSnapshot();
                snapshot.setId(UUID.randomUUID());
                snapshot.setPlanId(planId);
                snapshot.setOriginalModuleId(original.getId().toString());
                snapshot.setParentId(findCopiedModuleParentId(original.getParentId(), planId));
                snapshot.setName(original.getName());
                snapshot.setType(original.getType());
                snapshot.setSortOrder(original.getSortOrder());
                planModuleSnapshotMapper.insert(snapshot);
            }

            List<TestCaseNode> docNodes = testCaseNodeMapper.selectList(
                    new LambdaQueryWrapper<TestCaseNode>()
                            .eq(TestCaseNode::getDocumentId, documentId));

            String snapshotDocId = findSnapshotModuleId(documentId, planId);
            Set<String> caseIds = entry.getValue();

            for (TestCaseNode node : docNodes) {
                TestPlanNodeSnapshot nodeSnapshot = new TestPlanNodeSnapshot();
                nodeSnapshot.setId(UUID.randomUUID());
                nodeSnapshot.setPlanId(planId);
                nodeSnapshot.setOriginalNodeId(node.getId().toString());
                nodeSnapshot.setDocumentSnapshotId(snapshotDocId);
                nodeSnapshot.setParentId(findCopiedNodeParentId(node.getParentId(), planId));
                nodeSnapshot.setTitle(node.getTitle());
                nodeSnapshot.setType(node.getType());
                nodeSnapshot.setPriority(node.getPriority());
                nodeSnapshot.setIsAssociated(caseIds.contains(node.getId().toString()));
                nodeSnapshot.setLastResult("untested");
                nodeSnapshot.setSortOrder(node.getSortOrder());
                planNodeSnapshotMapper.insert(nodeSnapshot);
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

    private String findCopiedModuleParentId(String originalParentId, String planId) {
        if (originalParentId == null) {
            return null;
        }
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanModuleSnapshot>()
                        .eq(TestPlanModuleSnapshot::getPlanId, planId)
                        .eq(TestPlanModuleSnapshot::getOriginalModuleId, originalParentId));
        return snapshot != null ? snapshot.getId().toString() : null;
    }

    private String findSnapshotModuleId(String originalModuleId, String planId) {
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanModuleSnapshot>()
                        .eq(TestPlanModuleSnapshot::getPlanId, planId)
                        .eq(TestPlanModuleSnapshot::getOriginalModuleId, originalModuleId));
        return snapshot != null ? snapshot.getId().toString() : null;
    }

    private String findCopiedNodeParentId(String originalParentId, String planId) {
        if (originalParentId == null) {
            return null;
        }
        TestPlanNodeSnapshot snapshot = planNodeSnapshotMapper.selectOne(
                new LambdaQueryWrapper<TestPlanNodeSnapshot>()
                        .eq(TestPlanNodeSnapshot::getPlanId, planId)
                        .eq(TestPlanNodeSnapshot::getOriginalNodeId, originalParentId));
        return snapshot != null ? snapshot.getId().toString() : null;
    }

    private List<TestPlanSnapshotNodeRespDTO> pruneSnapshotTree(
            List<TestPlanSnapshotNodeRespDTO> allNodes) {

        Set<String> associatedIds = allNodes.stream()
                .filter(n -> Boolean.TRUE.equals(n.getIsAssociated()))
                .map(TestPlanSnapshotNodeRespDTO::getId)
                .collect(Collectors.toSet());

        Map<String, TestPlanSnapshotNodeRespDTO> nodeMap = allNodes.stream()
                .collect(Collectors.toMap(
                        TestPlanSnapshotNodeRespDTO::getId, n -> n));

        Set<String> keepIds = new HashSet<>(associatedIds);

        for (String assocId : associatedIds) {
            String parentId = nodeMap.get(assocId) != null ? nodeMap.get(assocId).getParentId() : null;
            while (parentId != null) {
                keepIds.add(parentId);
                TestPlanSnapshotNodeRespDTO parentNode = nodeMap.get(parentId);
                parentId = parentNode != null ? parentNode.getParentId() : null;
            }
        }

        for (String assocId : associatedIds) {
            collectDescendants(assocId, nodeMap, keepIds);
        }

        List<TestPlanSnapshotNodeRespDTO> filtered = allNodes.stream()
                .filter(n -> keepIds.contains(n.getId()))
                .collect(Collectors.toList());

        return buildSnapshotTree(filtered);
    }

    private void collectDescendants(String nodeId, Map<String, TestPlanSnapshotNodeRespDTO> nodeMap,
                                     Set<String> keepIds) {
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
                        n -> n.getParentId() != null ? n.getParentId() : "root"));

        List<TestPlanSnapshotNodeRespDTO> roots = parentMap.getOrDefault("root", new ArrayList<>());
        roots.forEach(root -> fillSnapshotChildren(root, parentMap));
        return roots;
    }

    private void fillSnapshotChildren(TestPlanSnapshotNodeRespDTO node,
                                       Map<String, List<TestPlanSnapshotNodeRespDTO>> parentMap) {
        List<TestPlanSnapshotNodeRespDTO> children = parentMap.getOrDefault(node.getId(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillSnapshotChildren(child, parentMap));
    }

    private TestPlanDetailRespDTO convertToDetailDTO(TestPlan plan) {
        TestPlanDetailRespDTO dto = new TestPlanDetailRespDTO();
        dto.setId(plan.getId().toString());
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setStatus(plan.getStatus());
        dto.setEnvironment(plan.getEnvironment());
        dto.setStartTime(plan.getStartTime());
        dto.setEndTime(plan.getEndTime());
        dto.setCreatedAt(plan.getCreatedAt());

        if (StringUtils.hasText(plan.getExecutorId())) {
            SysUser executor = userMapper.selectById(plan.getExecutorId());
            if (executor != null) {
                TestPlanDetailRespDTO.ExecutorInfo info = new TestPlanDetailRespDTO.ExecutorInfo();
                info.setId(executor.getId().toString());
                info.setName(executor.getUsername());
                dto.setExecutor(info);
            }
        }
        return dto;
    }

    private TestPlanSnapshotNodeRespDTO convertToSnapshotNodeDTO(TestPlanNodeSnapshot snapshot) {
        TestPlanSnapshotNodeRespDTO dto = new TestPlanSnapshotNodeRespDTO();
        dto.setId(snapshot.getId().toString());
        dto.setOriginalNodeId(snapshot.getOriginalNodeId());
        dto.setParentId(snapshot.getParentId());
        dto.setTitle(snapshot.getTitle());
        dto.setType(snapshot.getType());
        dto.setPriority(snapshot.getPriority());
        dto.setIsAssociated(snapshot.getIsAssociated());
        dto.setLastResult(snapshot.getLastResult());
        dto.setLastExecutorId(snapshot.getLastExecutorId());
        dto.setLastExecutedAt(snapshot.getLastExecutedAt());
        dto.setSortOrder(snapshot.getSortOrder());
        return dto;
    }
}
