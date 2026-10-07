package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.convert.TestCaseNodeConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestCaseNodeServiceImpl implements TestCaseNodeService {

    /** 优先级规范化（脑图详设 4.5，与产物采纳 PRIORITY_MAPPING 同口径） */
    private static final Map<String, String> PRIORITY_MAPPING = Map.of(
            "high", "P0", "medium", "P1", "low", "P2");

    /** 批量标记只允许落到用例与属性节点（用例无标签模型，type 即标记语义） */
    private static final Set<String> ALLOWED_TAG_TYPES = Set.of(
            Constants.NodeType.CASE, Constants.NodeType.PRECONDITION,
            Constants.NodeType.STEP, Constants.NodeType.EXPECTED);

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private TestCaseNodeConvertMapper testCaseNodeConvertMapper;
    /** 向量索引（WP-4.5）：节点标题变更触发整文档重嵌（详设 4.4） */
    @Resource
    private VectorIndexService vectorIndexService;

    @Override
    public TestCaseDocumentNodesRespDTO getDocumentNodes(UUID projectId, UUID documentId, UUID userId) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null || !document.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);

        List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(documentId);

        List<TestCaseNodeTreeRespDTO> dtos = nodes.stream()
                .map(this::convertToNodeDTO)
                .collect(Collectors.toList());

        TestCaseNodeTreeRespDTO rootNode = buildNodeTree(dtos);

        java.util.Map<String, Object> layoutJson = testCaseDocumentMapper.getLayout(documentId);

        TestCaseDocumentNodesRespDTO result = new TestCaseDocumentNodesRespDTO();
        result.setNode(rootNode);
        result.setLayout(layoutJson);
        return result;
    }

    @Override
    public TestCaseNodeTreeRespDTO getCaseDetail(UUID projectId, UUID caseId, UUID userId) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(node.getDocumentId());
        if (document == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        // 归属活动项目校验（SEC-014）：跨项目按用例节点不存在处理，不泄露用例存在性
        if (!document.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);
        // 悬停明细等场景需要完整用例结构：以该节点为根填充子孙（前置/步骤/预期）
        TestCaseNodeTreeRespDTO root = convertToNodeDTO(node);
        List<TestCaseNodeTreeRespDTO> dtos = testCaseNodeMapper.listByDocumentId(node.getDocumentId()).stream()
                .map(this::convertToNodeDTO)
                .collect(Collectors.toList());
        Map<String, List<TestCaseNodeTreeRespDTO>> parentMap = dtos.stream()
                .filter(n -> n.getParentId() != null)
                .collect(Collectors.groupingBy(n -> n.getParentId().toString()));
        fillChildren(root, parentMap);
        return root;
    }

    @Override
    public PageResult<TestCaseCaseListRespDTO> getCaseList(UUID projectId, UUID userId, String keyword,
                                                           String priority, Integer pageNo, Integer pageSize) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        // 查询项目下所有 document 的 ID
        // document_id 列使用 UUIDTypeHandler，必须传 UUID 而非字符串（否则强转失败）
        List<TestCaseDocument> documents = testCaseDocumentMapper.listByProjectId(projectId);
        List<UUID> documentIds = documents.stream()
                .map(TestCaseDocument::getId)
                .collect(Collectors.toList());

        if (documentIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }

        // 查询所有 case 节点，按标题/优先级过滤
        PageResult<TestCaseNode> page = testCaseNodeMapper.findCasePage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, documentIds, keyword, priority);

        // 构建 documentId → documentName 映射
        Map<String, String> docNameMap = documents.stream()
                .collect(Collectors.toMap(doc -> doc.getId().toString(), TestCaseDocument::getName));

        List<TestCaseCaseListRespDTO> dtos = page.getList().stream().map(node -> {
            TestCaseCaseListRespDTO dto = new TestCaseCaseListRespDTO();
            dto.setId(node.getId());
            dto.setTitle(node.getTitle());
            dto.setType(node.getType());
            dto.setPriority(node.getPriority());
            dto.setDocumentId(node.getDocumentId());
            dto.setDocumentName(docNameMap.get(node.getDocumentId().toString()));
            dto.setSortOrder(node.getSortOrder());
            dto.setVersion(node.getVersion());
            dto.setCreatedAt(node.getCreatedAt());
            dto.setUpdatedAt(node.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    // 同步重嵌失败需牵连本次保存回滚（4.4），故补事务边界（此前无 @Transactional）
    @Transactional(rollbackFor = Exception.class)
    public void updateCaseNode(UUID projectId, UUID caseId, UUID userId, TestCaseNodeUpdateReqDTO reqDTO) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        if (!Constants.NodeType.CASE.equals(node.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(node.getDocumentId());
        if (document == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        // 归属活动项目校验（SEC-014）：跨项目按用例节点不存在处理，不泄露用例存在性
        if (!document.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);
        // 更新载体只携带前端传入的字段，避免全列覆盖导致并发丢失更新
        TestCaseNode update = new TestCaseNode();
        update.setId(caseId);
        if (StringUtils.hasText(reqDTO.getTitle())) {
            update.setTitle(reqDTO.getTitle());
        }
        if (StringUtils.hasText(reqDTO.getPriority())) {
            update.setPriority(reqDTO.getPriority());
        }
        testCaseNodeMapper.updateById(update);
        if (StringUtils.hasText(reqDTO.getTitle())) {
            // 详设 4.4：节点标题是索引正文，标题保存同步重嵌，失败上抛整体回滚
            vectorIndexService.upsertTestCase(node.getDocumentId(), userId);
        }
    }

    // ---------- AI 助手确认执行落库（详设 04-ai-assistant 4.2⑥） ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID createCase(UUID projectId, UUID userId, UUID documentId, UUID parentId,
            Map<String, Object> fields) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        TestCaseDocument document = resolveCreateDocument(projectId, documentId);
        UUID targetParentId = resolveCreateParent(document.getId(), parentId);
        String title = text(fields == null ? null : fields.get("title"));
        if (title == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        TestCaseNode node = new TestCaseNode();
        node.setDocumentId(document.getId());
        node.setParentId(targetParentId);
        node.setType(Constants.NodeType.CASE);
        node.setTitle(title);
        node.setPriority(normalizePriority(fields.get("priority"), "P1"));
        node.setSortOrder(testCaseNodeMapper.listByParentId(targetParentId).size());
        node.setVersion(1);
        node.setAiGenerated(true);
        testCaseNodeMapper.insert(node);
        insertAttributeChildren(document.getId(), node.getId(), fields);
        // 标题与属性子节点是索引正文（详设 4.4）：落库与重嵌同事务，失败整体回滚
        vectorIndexService.upsertTestCase(document.getId(), userId);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCaseFields(UUID projectId, UUID userId, UUID caseId,
            List<Map<String, Object>> changes) {
        TestCaseNode node = requireCaseNode(projectId, caseId, userId, true);
        if (changes == null || changes.isEmpty()) {
            return;
        }
        // 更新载体只携带白名单内实际变更字段（C11 部分更新）
        TestCaseNode carrier = new TestCaseNode();
        carrier.setId(caseId);
        boolean reindex = false;
        boolean childrenChanged = false;
        for (Map<String, Object> change : changes) {
            if (change == null) {
                continue;
            }
            String field = text(change.get("field"));
            Object value = change.get("value");
            switch (field == null ? "" : field) {
                case "title" -> {
                    String title = text(value);
                    if (title != null) {
                        carrier.setTitle(title);
                        reindex = true;
                    }
                }
                case "priority" -> {
                    String priority = normalizePriority(value, null);
                    if (priority != null) {
                        carrier.setPriority(priority);
                    }
                }
                case "precondition", "steps", "expected" ->
                        childrenChanged |= applyAttribute(node, field, change.get("op"), value);
                default -> {
                    // 白名单外字段（详设 4.2⑥，update_case 不含 type）忽略
                }
            }
        }
        if (carrier.getTitle() != null || carrier.getPriority() != null) {
            testCaseNodeMapper.updateById(carrier);
        }
        if (childrenChanged) {
            resequenceChildren(caseId);
            reindex = true;
        }
        if (reindex) {
            vectorIndexService.upsertTestCase(node.getDocumentId(), userId);
        }
    }

    @Override
    public void tagCase(UUID projectId, UUID userId, UUID caseId, List<Map<String, Object>> changes) {
        // 批量标记沿脑图标记语义可作用于属性节点，故不守 type=case
        requireCaseNode(projectId, caseId, userId, false);
        if (changes == null || changes.isEmpty()) {
            return;
        }
        TestCaseNode carrier = new TestCaseNode();
        carrier.setId(caseId);
        for (Map<String, Object> change : changes) {
            if (change == null) {
                continue;
            }
            String field = text(change.get("field"));
            Object value = change.get("value");
            if ("priority".equals(field)) {
                String priority = normalizePriority(value, null);
                if (priority != null) {
                    carrier.setPriority(priority);
                }
            } else if ("type".equals(field)) {
                String type = text(value);
                if (type != null && ALLOWED_TAG_TYPES.contains(type)) {
                    carrier.setType(type);
                }
            }
        }
        // 取值全部被忽略时不做空更新（C11）
        if (carrier.getPriority() != null || carrier.getType() != null) {
            testCaseNodeMapper.updateById(carrier);
        }
    }

    // ---------- 助手执行辅助 ----------

    /** 助手执行目标守卫：存在 + 可选类型 + 文档属项目 + 成员（SEC-014 跨项目同不存在） */
    private TestCaseNode requireCaseNode(UUID projectId, UUID caseId, UUID userId, boolean caseOnly) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null || (caseOnly && !Constants.NodeType.CASE.equals(node.getType()))) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(node.getDocumentId());
        if (document == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        if (!document.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);
        return node;
    }

    /** 未指定文档取项目首份功能用例文档（listByProjectId 已按 sortOrder 升序） */
    private TestCaseDocument resolveCreateDocument(UUID projectId, UUID documentId) {
        if (documentId == null) {
            List<TestCaseDocument> documents = testCaseDocumentMapper.listByProjectId(projectId);
            if (documents.isEmpty()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
            }
            return documents.getFirst();
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null || !document.getProjectId().equals(projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    /** 未指定父节点挂文档自动根节点；显式父节点必须存在于同一文档 */
    private UUID resolveCreateParent(UUID documentId, UUID parentId) {
        if (parentId == null) {
            return testCaseNodeMapper.listByDocumentId(documentId).stream()
                    .filter(item -> item.getParentId() == null)
                    .findFirst()
                    .map(TestCaseNode::getId)
                    .orElseThrow(() -> ServiceExceptionUtil.get(
                            ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND));
        }
        TestCaseNode parent = testCaseNodeMapper.selectById(parentId);
        if (parent == null || !documentId.equals(parent.getDocumentId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        return parentId;
    }

    /** case 属性子节点（mindmap 详设 seed 口径）：precondition → step×n → expected×n 连续序号 */
    private void insertAttributeChildren(UUID documentId, UUID caseId, Map<String, Object> fields) {
        int order = 0;
        String precondition = text(fields.get("precondition"));
        if (precondition != null) {
            testCaseNodeMapper.insert(attributeNode(documentId, caseId,
                    Constants.NodeType.PRECONDITION, precondition, order++));
        }
        for (String step : textList(fields.get("steps"))) {
            testCaseNodeMapper.insert(attributeNode(documentId, caseId,
                    Constants.NodeType.STEP, step, order++));
        }
        for (String expected : textList(fields.get("expected"))) {
            testCaseNodeMapper.insert(attributeNode(documentId, caseId,
                    Constants.NodeType.EXPECTED, expected, order++));
        }
    }

    private static TestCaseNode attributeNode(UUID documentId, UUID parentId, String type,
            String title, int order) {
        TestCaseNode child = new TestCaseNode();
        child.setDocumentId(documentId);
        child.setParentId(parentId);
        child.setType(type);
        child.setTitle(title);
        child.setSortOrder(order);
        child.setAiGenerated(true);
        child.setVersion(1);
        return child;
    }

    /** 属性子节点变更：replace 删同类型重插、add 组尾占位追加；返回是否发生结构变化 */
    private boolean applyAttribute(TestCaseNode node, String field, Object op, Object value) {
        String type = switch (field) {
            case "precondition" -> Constants.NodeType.PRECONDITION;
            case "steps" -> Constants.NodeType.STEP;
            default -> Constants.NodeType.EXPECTED;
        };
        List<String> titles;
        if (Constants.NodeType.PRECONDITION.equals(type)) {
            String title = text(value);
            titles = title == null ? List.of() : List.of(title);
        } else {
            titles = textList(value);
        }

        List<TestCaseNode> sameType = testCaseNodeMapper.listByParentId(node.getId()).stream()
                .filter(child -> type.equals(child.getType()))
                .toList();
        boolean replace = "replace".equals(text(op));
        boolean changed = false;
        int order = 0;
        if (replace) {
            if (!sameType.isEmpty()) {
                testCaseNodeMapper.deleteByNodeIds(
                        sameType.stream().map(TestCaseNode::getId).toList());
                changed = true;
            }
        } else {
            // 组尾占位（详设 4.2⑥）：追加序号接同类型最大值之后，随后统一重排定型
            order = sameType.stream()
                    .map(TestCaseNode::getSortOrder)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(-1) + 1;
        }
        if (!titles.isEmpty()) {
            for (String title : titles) {
                testCaseNodeMapper.insert(attributeNode(node.getDocumentId(), node.getId(),
                        type, title, order++));
            }
            changed = true;
        }
        return changed;
    }

    /** 分组稳定重排：precondition → step → expected → other，组内按原序号稳定，序号连续化 */
    private void resequenceChildren(UUID caseId) {
        List<TestCaseNode> children = new ArrayList<>(testCaseNodeMapper.listByParentId(caseId));
        children.sort(Comparator
                .comparingInt((TestCaseNode child) -> groupOrder(child.getType()))
                .thenComparing((TestCaseNode child) -> child.getSortOrder() == null
                        ? Integer.MAX_VALUE : child.getSortOrder()));
        for (int i = 0; i < children.size(); i++) {
            TestCaseNode child = children.get(i);
            if (child.getSortOrder() == null || child.getSortOrder() != i) {
                testCaseNodeMapper.updateSortOrder(child.getId(), i);
            }
        }
    }

    private static int groupOrder(String type) {
        if (Constants.NodeType.PRECONDITION.equals(type)) {
            return 0;
        }
        if (Constants.NodeType.STEP.equals(type)) {
            return 1;
        }
        if (Constants.NodeType.EXPECTED.equals(type)) {
            return 2;
        }
        return 3;
    }

    /** P0–P3 大小写容忍，high / medium / low 映射 P0 / P1 / P2（脑图详设 4.5），其余取 fallback */
    private static String normalizePriority(Object raw, String fallback) {
        String value = text(raw);
        if (value == null) {
            return fallback;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.length() == 2 && upper.charAt(0) == 'P'
                && upper.charAt(1) >= '0' && upper.charAt(1) <= '3') {
            return upper;
        }
        return PRIORITY_MAPPING.getOrDefault(value.toLowerCase(Locale.ROOT), fallback);
    }

    /** 取标量文本：模型可能给复合值，按无效处理而不是落成 "{...}" 脏数据 */
    private static String text(Object value) {
        if (value == null || value instanceof Collection || value instanceof Map) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> textList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> elements) {
            List<String> items = new ArrayList<>();
            for (Object element : elements) {
                String text = text(element);
                if (text != null) {
                    items.add(text);
                }
            }
            return items;
        }
        String text = text(value);
        return text == null ? List.of() : List.of(text);
    }

    private TestCaseNodeTreeRespDTO buildNodeTree(List<TestCaseNodeTreeRespDTO> nodes) {
        Map<String, List<TestCaseNodeTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<TestCaseNodeTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots.isEmpty() ? null : roots.getFirst();
    }

    private void fillChildren(TestCaseNodeTreeRespDTO node,
                              Map<String, List<TestCaseNodeTreeRespDTO>> parentMap) {
        List<TestCaseNodeTreeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private TestCaseNodeTreeRespDTO convertToNodeDTO(TestCaseNode node) {
        return testCaseNodeConvertMapper.toTreeDTO(node);
    }
}
