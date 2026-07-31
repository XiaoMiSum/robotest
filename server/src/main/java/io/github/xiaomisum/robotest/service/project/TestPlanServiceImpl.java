package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestPlanConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCasesUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanRecordReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanExecutionRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanProgressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public PageResult<TestPlanListRespDTO> getPlanPage(UUID projectId, String status,
            String keyword, Integer pageNo, Integer pageSize) {
        PageResult<TestPlan> page = testPlanMapper.findPage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, projectId, keyword, status);

        // 列表展示进度/通过率：批量查本页全部关联用例快照，避免逐行 N+1
        List<UUID> planIds = page.getList().stream().map(TestPlan::getId).toList();
        Map<UUID, List<TestPlanNodeSnapshot>> snapshotsByPlan = planIds.isEmpty()
                ? Map.of()
                : planNodeSnapshotMapper.listAssociatedByPlanIds(planIds, Constants.NodeType.CASE)
                        .stream().collect(Collectors.groupingBy(TestPlanNodeSnapshot::getPlanId));

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

            List<TestPlanNodeSnapshot> snapshots = snapshotsByPlan.getOrDefault(plan.getId(), List.of());
            long passed = snapshots.stream()
                    .filter(s -> Constants.ExecutionResult.PASS.equals(s.getLastResult())).count();
            long untested = snapshots.stream()
                    .filter(s -> s.getLastResult() == null
                            || Constants.ExecutionResult.UNTESTED.equals(s.getLastResult())).count();
            long total = snapshots.size();
            dto.setTotalAssociated(total);
            dto.setPassed(passed);
            dto.setProgressPercent(total > 0
                    ? Math.round((total - untested) * 10000.0 / total) / 100.0
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
    public TestPlanDetailRespDTO createPlan(UUID projectId, UUID userId,
            TestPlanCreateReqDTO reqDTO) {
        TestPlan plan = TestPlanConvertMapper.INSTANCE.toEntity(reqDTO);
        plan.setProjectId(projectId);
        plan.setStatus(Constants.Status.NEW);
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

        List<TestPlanNodeSnapshot> allNodes = planNodeSnapshotMapper.listByPlanIdAndDocumentId(planId, documentId);
        List<TestPlanSnapshotNodeRespDTO> dtos = allNodes.stream()
                .map(this::convertToSnapshotNodeDTO)
                .collect(Collectors.toList());

        return pruneSnapshotTree(dtos);
    }

    @Override
    public List<SnapshotModuleTreeRespDTO> getPlanModuleTree(UUID planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        List<TestPlanModuleSnapshot> modules = planModuleSnapshotMapper.listSortedByPlanId(planId);

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
    public List<PlannedCasesRespDTO> getPlanPlannedCases(UUID planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        List<PlannedCasesRespDTO> result = new ArrayList<>();
        for (TestPlanModuleSnapshot docSnap : selectDocumentSnapshots(planId)) {
            List<UUID> caseIds = planNodeSnapshotMapper.listAssociatedByPlanIdAndDocumentId(planId, docSnap.getId())
                    .stream()
                    .map(TestPlanNodeSnapshot::getOriginalNodeId)
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
    public void updatePlanCases(UUID planId, TestPlanCasesUpdateReqDTO reqDTO) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        // 未结束（待开始/进行中）才允许调整规划
        if (!Constants.Status.NEW.equals(plan.getStatus())
                && !Constants.Status.IN_PROGRESS.equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_FINISHED);
        }

        Map<UUID, Set<UUID>> newSelection = new LinkedHashMap<>();
        for (TestPlanCreateReqDTO.SelectedNode sn : reqDTO.getSelectedNodes()) {
            TestCaseModule doc = testCaseModuleMapper.selectById(sn.getDocumentId());
            if (doc == null || !doc.getProjectId().equals(plan.getProjectId())
                    || !Constants.ModuleType.DOCUMENT.equals(doc.getType())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
            }
            newSelection.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
        }

        Map<UUID, TestPlanModuleSnapshot> existingDocs = selectDocumentSnapshots(planId).stream()
                .collect(Collectors.toMap(TestPlanModuleSnapshot::getOriginalModuleId, m -> m));

        // 1. 移除文档：删节点快照与文档快照（执行记录保留作审计），再清理空目录快照
        for (Map.Entry<UUID, TestPlanModuleSnapshot> entry : existingDocs.entrySet()) {
            if (newSelection.containsKey(entry.getKey())) {
                continue;
            }
            planNodeSnapshotMapper.deleteByPlanIdAndDocumentId(planId, entry.getValue().getId());
            planModuleSnapshotMapper.deleteById(entry.getValue().getId());
        }
        pruneEmptyDirectorySnapshots(planId);

        // 2. 新增文档：复用创建时的快照生成（内部已预置库中已有模块，不会重复复制目录）
        List<TestPlanCreateReqDTO.SelectedNode> added = reqDTO.getSelectedNodes().stream()
                .filter(sn -> !existingDocs.containsKey(sn.getDocumentId()))
                .collect(Collectors.toList());
        if (!added.isEmpty()) {
            generateSnapshots(planId, added);
        }

        // 3. 保留文档：补全快照后新增的节点，并按新选择重刷关联标记
        for (Map.Entry<UUID, Set<UUID>> entry : newSelection.entrySet()) {
            TestPlanModuleSnapshot docSnap = existingDocs.get(entry.getKey());
            if (docSnap != null) {
                refreshDocumentSnapshot(planId, docSnap, entry.getValue());
            }
        }
    }

    private List<TestPlanModuleSnapshot> selectDocumentSnapshots(UUID planId) {
        return planModuleSnapshotMapper.listByPlanIdAndType(planId, Constants.ModuleType.DOCUMENT);
    }

    // 移除文档后其祖先目录可能不再挂任何快照，自底向上循环清理，避免快照树残留空目录
    private void pruneEmptyDirectorySnapshots(UUID planId) {
        boolean removed = true;
        while (removed) {
            removed = false;
            List<TestPlanModuleSnapshot> all = planModuleSnapshotMapper.listByPlanId(planId);
            Set<UUID> referencedParents = all.stream()
                    .map(TestPlanModuleSnapshot::getParentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (TestPlanModuleSnapshot snap : all) {
                if (Constants.ModuleType.DIRECTORY.equals(snap.getType())
                        && !referencedParents.contains(snap.getId())) {
                    planModuleSnapshotMapper.deleteById(snap.getId());
                    removed = true;
                }
            }
        }
    }

    // 补全快照缺失节点（快照后新建的用例，sync 只更新不新增）并重刷 isAssociated
    private void refreshDocumentSnapshot(UUID planId, TestPlanModuleSnapshot docSnap, Set<UUID> caseIds) {
        Map<UUID, TestPlanNodeSnapshot> snapByOriginal = planNodeSnapshotMapper.listByPlanIdAndDocumentId(planId, docSnap.getId())
                .stream()
                .filter(s -> s.getOriginalNodeId() != null)
                .collect(Collectors.toMap(TestPlanNodeSnapshot::getOriginalNodeId, s -> s, (a, b) -> a));

        Map<UUID, TestCaseNode> currentById = testCaseNodeMapper.listByDocumentId(docSnap.getOriginalModuleId())
                .stream()
                .collect(Collectors.toMap(TestCaseNode::getId, n -> n));

        for (TestCaseNode node : currentById.values()) {
            ensureNodeSnapshot(planId, docSnap.getId(), node, currentById, snapByOriginal, caseIds);
        }

        for (TestPlanNodeSnapshot snap : snapByOriginal.values()) {
            boolean associated = caseIds.contains(snap.getOriginalNodeId());
            if (!Objects.equals(associated, snap.getIsAssociated())) {
                // 仅回写关联标记，避免整行覆盖并发产生的执行结果
                TestPlanNodeSnapshot snapUpdate = new TestPlanNodeSnapshot();
                snapUpdate.setId(snap.getId());
                snapUpdate.setIsAssociated(associated);
                planNodeSnapshotMapper.updateById(snapUpdate);
            }
        }
    }

    // 递归保证父链先于子节点入快照（库返回顺序任意，逆序插入会产生父映射落空的孤儿根），返回该节点的快照 ID
    private UUID ensureNodeSnapshot(UUID planId, UUID docSnapshotId, TestCaseNode node,
            Map<UUID, TestCaseNode> currentById,
            Map<UUID, TestPlanNodeSnapshot> snapByOriginal,
            Set<UUID> associatedCaseIds) {
        TestPlanNodeSnapshot existing = snapByOriginal.get(node.getId());
        if (existing != null) {
            return existing.getId();
        }
        UUID parentSnapshotId = null;
        if (node.getParentId() != null) {
            TestCaseNode parent = currentById.get(node.getParentId());
            if (parent != null) {
                parentSnapshotId = ensureNodeSnapshot(planId, docSnapshotId, parent, currentById, snapByOriginal,
                        associatedCaseIds);
            }
        }
        TestPlanNodeSnapshot snapshot = new TestPlanNodeSnapshot();
        snapshot.setPlanId(planId);
        snapshot.setOriginalNodeId(node.getId());
        snapshot.setDocumentSnapshotId(docSnapshotId);
        snapshot.setParentId(parentSnapshotId);
        snapshot.setTitle(node.getTitle());
        snapshot.setType(node.getType());
        snapshot.setPriority(node.getPriority());
        snapshot.setIsAssociated(associatedCaseIds.contains(node.getId()));
        snapshot.setLastResult(Constants.ExecutionResult.UNTESTED);
        snapshot.setSortOrder(node.getSortOrder());
        snapshot.setAiGenerated(node.getAiGenerated());
        planNodeSnapshotMapper.insert(snapshot);
        snapByOriginal.put(node.getId(), snapshot);
        return snapshot.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExecutionRecord(UUID planId, UUID userId,
            TestPlanRecordReqDTO reqDTO) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
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

        String result = reqDTO.getResult();
        if (!Constants.ExecutionResult.PASS.equals(result) && !Constants.ExecutionResult.FAIL.equals(result)
                && !Constants.ExecutionResult.BLOCK.equals(result)
                && !Constants.ExecutionResult.UNTESTED.equals(result)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        // 更新载体只携带本次标记字段，避免全列覆盖导致并发丢失更新
        TestPlanNodeSnapshot snapUpdate = new TestPlanNodeSnapshot();
        snapUpdate.setId(snapshotNode.getId());
        snapUpdate.setLastResult(result);
        snapUpdate.setLastExecutorId(userId);
        snapUpdate.setLastExecutedAt(LocalDateTime.now());
        planNodeSnapshotMapper.updateById(snapUpdate);

        // 需求：标记执行结果后待开始计划自动转入进行中（已取代单独的开始执行操作）
        if (Constants.Status.NEW.equals(plan.getStatus())) {
            TestPlan planUpdate = new TestPlan();
            planUpdate.setId(plan.getId());
            planUpdate.setStatus(Constants.Status.IN_PROGRESS);
            testPlanMapper.updateById(planUpdate);
        }

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
        List<TestPlanExecutionRecord> records = planExecutionRecordMapper.listByPlanIdAndNodeId(planId, nodeId);

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
        // 已结束的计划快照已定格，不再允许同步；待开始/进行中均允许
        if (!Constants.Status.NEW.equals(plan.getStatus())
                && !Constants.Status.IN_PROGRESS.equals(plan.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_FINISHED);
        }

        List<TestPlanNodeSnapshot> snapshotNodes = planNodeSnapshotMapper.listByPlanId(planId);

        // 1. 同步模块快照：名称、排序与原始模块保持一致；已删除的模块移除快照
        List<TestPlanModuleSnapshot> snapshotModules = planModuleSnapshotMapper.listByPlanId(planId);

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
                // 原始模块仍存在，同步名称和排序；载体只携带同步字段，避免整行覆盖并发变更
                TestPlanModuleSnapshot moduleUpdate = new TestPlanModuleSnapshot();
                moduleUpdate.setId(moduleSnap.getId());
                moduleUpdate.setName(originalModule.getName());
                moduleUpdate.setSortOrder(originalModule.getSortOrder());
                planModuleSnapshotMapper.updateById(moduleUpdate);
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
            // 载体只携带同步字段，避免整行覆盖并发写入的执行结果
            TestPlanNodeSnapshot nodeUpdate = new TestPlanNodeSnapshot();
            nodeUpdate.setId(snapshot.getId());
            if (currentNode == null || currentNode.getIsDeleted()) {
                nodeUpdate.setIsDeleted(true);
            } else {
                nodeUpdate.setTitle(currentNode.getTitle());
                nodeUpdate.setType(currentNode.getType());
                nodeUpdate.setPriority(currentNode.getPriority());
                nodeUpdate.setSortOrder(currentNode.getSortOrder());
                nodeUpdate.setAiGenerated(currentNode.getAiGenerated());
            }
            planNodeSnapshotMapper.updateById(nodeUpdate);
        }
    }

    @Override
    public TestPlanProgressRespDTO getPlanProgress(UUID planId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }

        List<TestPlanNodeSnapshot> snapshots = planNodeSnapshotMapper.listAssociatedByPlanId(planId, Constants.NodeType.CASE);

        TestPlanProgressRespDTO dto = new TestPlanProgressRespDTO();
        dto.setTotalAssociated(snapshots.size());

        long passed = 0, failed = 0, blocked = 0, untested = 0;
        for (TestPlanNodeSnapshot snap : snapshots) {
            String result = snap.getLastResult();
            if (result == null || Constants.ExecutionResult.UNTESTED.equals(result)) {
                untested++;
            } else {
                switch (result) {
                    case Constants.ExecutionResult.PASS -> passed++;
                    case Constants.ExecutionResult.FAIL -> failed++;
                    // 历史脏数据兼容：旧统计误用 blocked，前端一贯发 block
                    case Constants.ExecutionResult.BLOCK, "blocked" -> blocked++;
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

        long untestedCount = planNodeSnapshotMapper.countUntestedAssociatedByPlanId(planId, Constants.Status.UNTESTED);
        if (untestedCount > 0) {
            log.warn("Plan {} closed with {} untested associated cases", planId, untestedCount);
        }

        TestPlan update = new TestPlan();
        update.setId(planId);
        update.setStatus(Constants.Status.CLOSED);
        testPlanMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePlan(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        TestPlan update = new TestPlan();
        update.setId(planId);
        update.setStatus(Constants.Status.COMPLETED);
        testPlanMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(UUID planId, UUID userId) {
        TestPlan plan = testPlanMapper.selectById(planId);
        if (plan == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_PLAN_NOT_FOUND);
        }
        if (!userId.equals(plan.getExecutorId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        // 无物理外键，需显式级联删除快照与执行记录
        planExecutionRecordMapper.deleteByPlanId(planId);
        planNodeSnapshotMapper.deleteByPlanId(planId);
        planModuleSnapshotMapper.deleteByPlanId(planId);
        testPlanMapper.deleteById(planId);
    }

    private void generateSnapshots(UUID planId, List<TestPlanCreateReqDTO.SelectedNode> selectedNodes) {
        Map<UUID, Set<UUID>> docCaseMap = new LinkedHashMap<>();
        for (TestPlanCreateReqDTO.SelectedNode sn : selectedNodes) {
            docCaseMap.put(sn.getDocumentId(), new HashSet<>(sn.getCaseIds()));
        }

        Set<UUID> copiedModuleIds = planModuleSnapshotMapper.listByPlanId(planId)
                .stream()
                .map(TestPlanModuleSnapshot::getOriginalModuleId)
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
                TestPlanModuleSnapshot snapshot = new TestPlanModuleSnapshot();
                snapshot.setPlanId(planId);
                snapshot.setOriginalModuleId(original.getId());
                snapshot.setParentId(findCopiedModuleParentId(original.getParentId(), planId));
                snapshot.setName(original.getName());
                snapshot.setType(original.getType());
                snapshot.setSortOrder(original.getSortOrder());
                planModuleSnapshotMapper.insert(snapshot);
            }

            List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(documentId);

            UUID snapshotDocId = findSnapshotModuleId(documentId, planId);
            Set<UUID> caseIds = entry.getValue();

            // 递归插入保证父先于子，避免库返回顺序导致父映射落空
            Map<UUID, TestCaseNode> currentById = docNodes.stream()
                    .collect(Collectors.toMap(TestCaseNode::getId, n -> n));
            Map<UUID, TestPlanNodeSnapshot> snapByOriginal = new HashMap<>();
            for (TestCaseNode node : docNodes) {
                ensureNodeSnapshot(planId, snapshotDocId, node, currentById, snapByOriginal, caseIds);
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
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.findByPlanIdAndOriginalModuleId(planId, originalParentId);
        return snapshot != null ? snapshot.getId() : null;
    }

    private UUID findSnapshotModuleId(UUID originalModuleId, UUID planId) {
        TestPlanModuleSnapshot snapshot = planModuleSnapshotMapper.findByPlanIdAndOriginalModuleId(planId, originalModuleId);
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
