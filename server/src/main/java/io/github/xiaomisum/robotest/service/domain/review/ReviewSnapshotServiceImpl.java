package io.github.xiaomisum.robotest.service.domain.review;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestReviewConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.PlannedCasesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewSnapshotNodeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.SnapshotModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReviewSnapshotServiceImpl implements ReviewSnapshotService {

    private final TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;
    private final TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    private final TestCaseDocumentMapper testCaseDocumentMapper;
    private final ProjectModuleMapper projectModuleMapper;
    private final TestCaseNodeMapper testCaseNodeMapper;

    public ReviewSnapshotServiceImpl(
            TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper,
            TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper,
            TestCaseDocumentMapper testCaseDocumentMapper,
            ProjectModuleMapper projectModuleMapper,
            TestCaseNodeMapper testCaseNodeMapper) {
        this.reviewModuleSnapshotMapper = reviewModuleSnapshotMapper;
        this.reviewNodeSnapshotMapper = reviewNodeSnapshotMapper;
        this.testCaseDocumentMapper = testCaseDocumentMapper;
        this.projectModuleMapper = projectModuleMapper;
        this.testCaseNodeMapper = testCaseNodeMapper;
    }

    @Override
    public void generateSnapshots(UUID reviewId, List<TestReviewCreateReqDTO.SelectedNode> selectedNodes) {
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

                // 根据路径节点类型查不同表：document→test_case_document, directory→project_module
                String name = null;
                UUID parentId = null;
                Integer sortOrder = null;
                String type = null;
                TestCaseDocument doc = testCaseDocumentMapper.selectById(moduleId);
                if (doc != null) {
                    name = doc.getName();
                    parentId = doc.getModuleId();
                    sortOrder = doc.getSortOrder();
                    type = Constants.ModuleType.DOCUMENT;
                } else {
                    ProjectModule mod = projectModuleMapper.selectById(moduleId);
                    if (mod != null) {
                        name = mod.getName();
                        parentId = mod.getParentId();
                        sortOrder = mod.getSortOrder();
                        type = Constants.ModuleType.DIRECTORY;
                    }
                }
                if (name == null) {
                    continue;
                }
                TestReviewModuleSnapshot snapshot = new TestReviewModuleSnapshot();
                snapshot.setReviewId(reviewId);
                snapshot.setOriginalModuleId(moduleId);
                snapshot.setParentId(findCopiedParentId(parentId, copiedModuleIds, reviewId));
                snapshot.setName(name);
                snapshot.setType(type);
                snapshot.setSortOrder(sortOrder);
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

    @Override
    public void updateCases(UUID reviewId, UUID projectId,
            List<TestReviewCreateReqDTO.SelectedNode> selectedNodes) {

        // 批量校验选中文档：一次 IN 查询替代逐条 selectById，避免 N+1
        Set<UUID> selectedDocIds = selectedNodes.stream()
                .map(TestReviewCreateReqDTO.SelectedNode::getDocumentId)
                .collect(Collectors.toSet());
        Map<UUID, TestCaseDocument> docsById = testCaseDocumentMapper.listByIds(selectedDocIds).stream()
                .collect(Collectors.toMap(TestCaseDocument::getId, m -> m));

        Map<UUID, Set<UUID>> newSelection = new LinkedHashMap<>();
        for (TestReviewCreateReqDTO.SelectedNode sn : selectedNodes) {
            TestCaseDocument doc = docsById.get(sn.getDocumentId());
            if (doc == null || !doc.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
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
            reviewNodeSnapshotMapper.deleteByReviewIdAndDocumentId(reviewId, entry.getValue().getId());
            reviewModuleSnapshotMapper.deleteById(entry.getValue().getId());
        }
        pruneEmptyDirectorySnapshots(reviewId);

        // 2. 新增文档：复用创建时的快照生成（内部已预置库中已有模块，不会重复复制目录）
        List<TestReviewCreateReqDTO.SelectedNode> added = selectedNodes.stream()
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

    @Override
    public void syncSnapshots(UUID reviewId) {
        List<TestReviewNodeSnapshot> snapshotNodes = reviewNodeSnapshotMapper.listByReviewId(reviewId);

        // 1. 同步模块快照：名称、排序与原始模块保持一致；已删除的模块移除快照
        List<TestReviewModuleSnapshot> snapshotModules = reviewModuleSnapshotMapper.listByReviewId(reviewId);

        Set<UUID> validModuleSnapshotIds = new HashSet<>();
        for (TestReviewModuleSnapshot moduleSnap : snapshotModules) {
            if (moduleSnap.getOriginalModuleId() == null) {
                validModuleSnapshotIds.add(moduleSnap.getId());
                continue;
            }
            // 根据快照类型查不同表：document→test_case_document, directory→project_module
            String name = null;
            Integer sortOrder = null;
            Boolean isDeleted = null;
            if (Constants.ModuleType.DOCUMENT.equals(moduleSnap.getType())) {
                TestCaseDocument doc = testCaseDocumentMapper.selectById(moduleSnap.getOriginalModuleId());
                if (doc != null) {
                    name = doc.getName();
                    sortOrder = doc.getSortOrder();
                    isDeleted = doc.getIsDeleted();
                }
            } else {
                ProjectModule mod = projectModuleMapper.selectById(moduleSnap.getOriginalModuleId());
                if (mod != null) {
                    name = mod.getName();
                    sortOrder = mod.getSortOrder();
                    isDeleted = mod.getIsDeleted();
                }
            }
            if (name == null || Boolean.TRUE.equals(isDeleted)) {
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
                moduleUpdate.setName(name);
                moduleUpdate.setSortOrder(sortOrder);
                reviewModuleSnapshotMapper.updateById(moduleUpdate);
                validModuleSnapshotIds.add(moduleSnap.getId());
            }
        }

        // 批量加载快照引用的原始节点，避免循环内逐条 selectById（N+1）
        Map<UUID, TestCaseNode> originalNodesById = testCaseNodeMapper
                .listByIds(snapshotNodes.stream()
                        .map(TestReviewNodeSnapshot::getOriginalNodeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(TestCaseNode::getId, n -> n));

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
            TestCaseNode currentNode = originalNodesById.get(snapshot.getOriginalNodeId());
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
                nodeUpdate.setAiGenerated(currentNode.getAiGenerated());
            }
            reviewNodeSnapshotMapper.updateById(nodeUpdate);
        }
    }

    @Override
    public void deleteByReviewId(UUID reviewId) {
        reviewNodeSnapshotMapper.deleteByReviewId(reviewId);
        reviewModuleSnapshotMapper.deleteByReviewId(reviewId);
    }

    @Override
    public TestReviewNodeSnapshot getNode(UUID snapshotNodeId) {
        return reviewNodeSnapshotMapper.selectById(snapshotNodeId);
    }

    @Override
    public void resetMarkAsPending(UUID snapshotNodeId, UUID reviewerId, LocalDateTime reviewedAt) {
        reviewNodeSnapshotMapper.resetLastMarkAsPending(snapshotNodeId, reviewerId, reviewedAt);
    }

    @Override
    public void applyMark(UUID snapshotNodeId, UUID reviewerId, String mark, LocalDateTime reviewedAt) {
        // 更新载体只携带本次标记字段，避免全列覆盖导致并发丢失更新
        TestReviewNodeSnapshot snapUpdate = new TestReviewNodeSnapshot();
        snapUpdate.setId(snapshotNodeId);
        snapUpdate.setLastMark(mark);
        snapUpdate.setLastReviewerId(reviewerId);
        snapUpdate.setLastReviewedAt(reviewedAt);
        reviewNodeSnapshotMapper.updateById(snapUpdate);
    }

    @Override
    public List<TestReviewSnapshotNodeRespDTO> getSnapshotTree(UUID reviewId, UUID documentId) {
        List<TestReviewNodeSnapshot> allNodes = reviewNodeSnapshotMapper
                .listByReviewIdAndDocumentId(reviewId, documentId);
        List<TestReviewSnapshotNodeRespDTO> dtos = allNodes.stream()
                .map(this::toSnapshotNodeDTO)
                .collect(Collectors.toList());

        return pruneSnapshotTree(dtos);
    }

    @Override
    public List<SnapshotModuleTreeRespDTO> getModuleTree(UUID reviewId) {
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

    @Override
    public List<PlannedCasesRespDTO> getPlannedCases(UUID reviewId) {
        List<PlannedCasesRespDTO> result = new ArrayList<>();
        for (TestReviewModuleSnapshot docSnap : selectDocumentSnapshots(reviewId)) {
            List<UUID> caseIds = reviewNodeSnapshotMapper
                    .listAssociatedByReviewIdAndDocumentId(reviewId, docSnap.getId())
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
    public List<TestReviewNodeSnapshot> listAssociatedByReviewIds(Collection<UUID> reviewIds, String type) {
        return reviewNodeSnapshotMapper.listAssociatedByReviewIds(reviewIds, type);
    }

    @Override
    public List<TestReviewNodeSnapshot> listAssociatedByReviewId(UUID reviewId, String type) {
        return reviewNodeSnapshotMapper.listAssociatedByReviewId(reviewId, type);
    }

    private List<TestReviewModuleSnapshot> selectDocumentSnapshots(UUID reviewId) {
        return reviewModuleSnapshotMapper.listByReviewIdAndType(reviewId, Constants.ModuleType.DOCUMENT);
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
        Map<UUID, TestReviewNodeSnapshot> snapByOriginal = reviewNodeSnapshotMapper
                .listByReviewIdAndDocumentId(reviewId, docSnap.getId())
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
        snapshot.setAiGenerated(node.getAiGenerated());
        reviewNodeSnapshotMapper.insert(snapshot);
        snapByOriginal.put(node.getId(), snapshot);
        return snapshot.getId();
    }

    private List<UUID> getModulePath(UUID documentId) {
        List<UUID> path = new ArrayList<>();
        UUID currentId = documentId;
        while (currentId != null) {
            path.add(0, currentId);
            // 尝试 project_module（目录节点）
            ProjectModule module = projectModuleMapper.selectById(currentId);
            if (module != null) {
                currentId = module.getParentId();
                continue;
            }
            // 尝试 test_case_document（文档节点，叶子节点）
            TestCaseDocument doc = testCaseDocumentMapper.selectById(currentId);
            currentId = doc != null ? doc.getModuleId() : null;
        }
        return path;
    }

    private UUID findCopiedParentId(UUID originalParentId, Set<UUID> copiedModuleIds, UUID reviewId) {
        if (originalParentId == null) {
            return null;
        }
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper
                .findByReviewIdAndOriginalModuleId(reviewId, originalParentId);
        return snapshot != null ? snapshot.getId() : null;
    }

    private UUID findSnapshotModuleId(UUID originalModuleId, UUID reviewId) {
        TestReviewModuleSnapshot snapshot = reviewModuleSnapshotMapper
                .findByReviewIdAndOriginalModuleId(reviewId, originalModuleId);
        return snapshot != null ? snapshot.getId() : null;
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

    private TestReviewSnapshotNodeRespDTO toSnapshotNodeDTO(TestReviewNodeSnapshot snapshot) {
        return TestReviewConvertMapper.INSTANCE.toSnapshotNodeDTO(snapshot);
    }
}