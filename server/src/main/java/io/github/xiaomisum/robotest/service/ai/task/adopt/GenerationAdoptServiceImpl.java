package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiArtifactConfirm;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiArtifactConfirmMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.TestDesignGenerationHandler;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ProjectModuleService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseDocumentService;
import io.github.xiaomisum.robotest.service.trace.TraceEdgeWriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 生成链产物采纳（生成链详设 3.5 采纳事务）：case:edit 资源权限（1000018208）→
 * 按 kind 落库（委托既有创建服务）→ 同事务写 AI 派生 derivation 边 → adopted_ref 回写；
 * 疑似重复的产物仅改名后可采纳（1000018204），父级未采纳不落库（1000018210）。
 */
@Slf4j
@Service
public class GenerationAdoptServiceImpl implements GenerationAdoptService, ArtifactAdopter {

    private static final String PERMISSION_ADOPT = "case:edit";
    private static final String PLACEMENT_ATTACH = "attach";
    private static final String FIELD_PLACEMENT = "placement";
    private static final String FIELD_TARGET_MODULE = "targetModuleId";
    /** 脑图用例节点版本锚定格式（追溯矩阵 2.4 target_version，v1 起） */
    private static final String VERSION_PREFIX = "v";
    private static final Map<String, String> PRIORITY_MAPPING = Map.of(
            "high", "P0", "medium", "P1", "low", "P2");

    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiArtifactConfirmMapper confirmMapper;
    @Resource
    private ProjectModuleService projectModuleService;
    @Resource
    private TestCaseDocumentService testCaseDocumentService;
    @Resource
    private TraceEdgeWriter traceEdgeWriter;
    @Resource
    private VectorIndexService vectorIndexService;

    @Override
    public String type() {
        return TestDesignGenerationHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        requirePermission(context.loginUser());
        String kind = asString(context.artifact().get("kind"));
        if (Constants.AiArtifactKind.MODULE.equals(kind)) {
            return adoptModule(context);
        }
        if (Constants.AiArtifactKind.MINDMAP_DOCUMENT.equals(kind)) {
            return adoptDocument(context);
        }
        if (Constants.AiArtifactKind.TEST_CASE.equals(kind)) {
            return adoptNode(context);
        }
        // 生成链仅三类产物，未知 kind 视为确认入参非法（3.6.5 逐项校验）
        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
    }

    // ---------- 模块 ----------

    private AdoptOutcome adoptModule(AdoptContext context) {
        Map<String, Object> content = resolveContent(context);
        guardSuspectedDuplicate(context, content);
        ProjectModuleCreateReqDTO reqDTO = new ProjectModuleCreateReqDTO();
        reqDTO.setParentId(resolveTargetModule(context));
        reqDTO.setName(requiredText(content, "name"));
        ProjectModuleTreeRespDTO created = projectModuleService.createModule(
                context.projectId(), context.operatorId(), reqDTO);
        traceEdgeWriter.writeAiDerivationEdges(context.projectId(), sourceRequirementIds(context, content),
                Constants.TraceNodeType.MODULE, created.getId(), null);
        return outcome(context, created.getId(), "moduleId", String.valueOf(created.getId()));
    }

    // ---------- 脑图文档 ----------

    private AdoptOutcome adoptDocument(AdoptContext context) {
        Map<String, Object> content = resolveContent(context);
        guardSuspectedDuplicate(context, content);
        TestCaseDocumentCreateReqDTO reqDTO = new TestCaseDocumentCreateReqDTO();
        reqDTO.setModuleId(resolveDocumentModule(context));
        reqDTO.setName(requiredText(content, "name"));
        TestCaseDocumentRespDTO created = testCaseDocumentService.createTestCase(
                context.projectId(), context.operatorId(), reqDTO);
        traceEdgeWriter.writeAiDerivationEdges(context.projectId(), sourceRequirementIds(context, content),
                Constants.TraceNodeType.MINDMAP_DOCUMENT, created.getId(), null);
        return outcome(context, created.getId(), "documentId", String.valueOf(created.getId()));
    }

    /**
     * 文档落位：target.moduleId 显式优先；否则父模块产物必须已采纳（1000018210），
     * 无父级 key（顶层）才按提交时 placement 兜底（详设 4.3①③）。
     */
    private UUID resolveDocumentModule(AdoptContext context) {
        if (context.targetModuleId() != null) {
            return validateModule(context.projectId(), context.targetModuleId());
        }
        String parentKey = asString(context.artifact().get("parentKey"));
        if (parentKey != null) {
            UUID moduleId = adoptedRefId(context, parentKey, "moduleId");
            if (moduleId == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_PARENT_NOT_ADOPTED);
            }
            return validateModule(context.projectId(), moduleId);
        }
        return validateModule(context.projectId(),
                PLACEMENT_ATTACH.equals(placementOf(context)) ? inputModuleId(context) : null);
    }

    // ---------- 用例节点 ----------

    private AdoptOutcome adoptNode(AdoptContext context) {
        Map<String, Object> content = resolveContent(context);
        guardSuspectedDuplicate(context, content);
        String title = requiredText(content, "title");

        UUID documentId = adoptedRefId(context, asString(context.artifact().get("parentKey")), "documentId");
        if (documentId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_PARENT_NOT_ADOPTED);
        }
        UUID parentId = resolveNodeParent(context, documentId);
        boolean testCase = Boolean.TRUE.equals(content.get("isTestCase"));
        int sortOrder = testCaseNodeMapper.listByParentId(parentId).size();

        TestCaseNode node = new TestCaseNode();
        node.setDocumentId(documentId);
        node.setParentId(parentId);
        node.setTitle(title);
        node.setSortOrder(sortOrder);
        node.setAiGenerated(true);
        node.setVersion(1);
        if (!testCase) {
            node.setType(Constants.NodeType.NORMAL);
            testCaseNodeMapper.insert(node);
            vectorIndexService.upsertTestCase(documentId, context.operatorId());
            return outcome(context, node.getId(), nodeRef(documentId, node.getId()));
        }

        Map<String, Object> attributes = asAttributes(content.get("attributes"));
        node.setType(Constants.NodeType.CASE);
        node.setPriority(mapPriority(asString(attributes.get("priority"))));
        testCaseNodeMapper.insert(node);
        insertAttributeChildren(documentId, node.getId(), attributes);
        traceEdgeWriter.writeAiDerivationEdges(context.projectId(), sourceRequirementIds(context, content),
                Constants.TraceNodeType.TEST_CASE, node.getId(), VERSION_PREFIX + node.getVersion());
        vectorIndexService.upsertTestCase(documentId, context.operatorId());
        Map<String, Object> adoptedRef = nodeRef(documentId, node.getId());
        adoptedRef.put("caseId", String.valueOf(node.getId()));
        return outcome(context, node.getId(), adoptedRef);
    }

    /** 文档内父节点：parentRef 为空挂根（自动创建的根节点），否则取父节点产物的 nodeId（4.3③） */
    private UUID resolveNodeParent(AdoptContext context, UUID documentId) {
        String parentRef = trimToNull(asString(contentOf(context.artifact()).get("parentRef")));
        if (parentRef == null) {
            return testCaseNodeMapper.listByDocumentId(documentId).stream()
                    .filter(item -> item.getParentId() == null)
                    .findFirst()
                    .map(TestCaseNode::getId)
                    .orElseThrow(() -> ServiceExceptionUtil.get(
                            ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND));
        }
        UUID parentId = adoptedRefId(context, parentRef, "nodeId");
        if (parentId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_PARENT_NOT_ADOPTED);
        }
        return parentId;
    }

    /** case 属性子节点（mindmap 详设 seed 口径）：precondition → step×n → expected×n 顺序号连排 */
    private void insertAttributeChildren(UUID documentId, UUID caseId, Map<String, Object> attributes) {
        String precondition = trimToNull(asString(attributes.get("precondition")));
        List<String> steps = asStringList(attributes.get("steps"));
        List<String> expected = asStringList(attributes.get("expected"));
        int order = 0;
        if (precondition != null) {
            testCaseNodeMapper.insert(childNode(documentId, caseId,
                    Constants.NodeType.PRECONDITION, precondition, order++));
        }
        for (String step : steps) {
            testCaseNodeMapper.insert(childNode(documentId, caseId, Constants.NodeType.STEP, step, order++));
        }
        for (String item : expected) {
            testCaseNodeMapper.insert(childNode(documentId, caseId, Constants.NodeType.EXPECTED, item, order++));
        }
    }

    private static TestCaseNode childNode(UUID documentId, UUID parentId, String type, String title, int order) {
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

    private static Map<String, Object> nodeRef(UUID documentId, UUID nodeId) {
        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("documentId", String.valueOf(documentId));
        adoptedRef.put("nodeId", String.valueOf(nodeId));
        return adoptedRef;
    }

    /** 产物 priority（high/medium/low）→ 用例节点优先级（P0-P3，mindmap 详设 4.5） */
    private static String mapPriority(String priority) {
        if (priority == null) {
            return "P1";
        }
        return PRIORITY_MAPPING.getOrDefault(priority, "P1");
    }

    // ---------- 共用 ----------

    private static void requirePermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION_ADOPT)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_NO_ADOPT_PERMISSION);
        }
    }

    /** 疑似重复的产物只有改名后（adopted_edited）可采纳，避免重复数据落库 */
    private static void guardSuspectedDuplicate(AdoptContext context, Map<String, Object> content) {
        Object duplicate = content.get("suspectedDuplicateOf");
        if (duplicate != null && !asString(duplicate).isBlank()
                && Constants.AiArtifactAction.ADOPTED.equals(context.action())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_ARTIFACT_DUPLICATE);
        }
    }

    /** adopted 直用产物内容；adopted_edited 以编辑值覆盖原产物（整包或局部编辑均可） */
    private static Map<String, Object> resolveContent(AdoptContext context) {
        Map<String, Object> original = contentOf(context.artifact());
        if (Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())
                && context.content() != null) {
            Map<String, Object> merged = new LinkedHashMap<>(original);
            merged.putAll(context.content());
            return merged;
        }
        return original;
    }

    private static String requiredText(Map<String, Object> content, String field) {
        String value = trimToNull(asString(content.get(field)));
        if (value == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        return value;
    }

    private UUID resolveTargetModule(AdoptContext context) {
        UUID moduleId = context.targetModuleId();
        if (moduleId == null && PLACEMENT_ATTACH.equals(placementOf(context))) {
            moduleId = inputModuleId(context);
        }
        return validateModule(context.projectId(), moduleId);
    }

    private UUID validateModule(UUID projectId, UUID moduleId) {
        if (moduleId == null) {
            return null;
        }
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null || !projectId.equals(module.getProjectId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_TARGET_MODULE_INVALID);
        }
        return moduleId;
    }

    private static UUID inputModuleId(AdoptContext context) {
        Object raw = context.task().getInput() == null ? null
                : context.task().getInput().get(FIELD_TARGET_MODULE);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String placementOf(AdoptContext context) {
        Object raw = context.task().getInput() == null ? null
                : context.task().getInput().get(FIELD_PLACEMENT);
        return raw == null ? "new_top_level" : String.valueOf(raw);
    }

    /** 父级产物已采纳的引用值；无记录或未采纳返回 null（4.3③ 依赖顺序） */
    private UUID adoptedRefId(AdoptContext context, String artifactKey, String field) {
        if (artifactKey == null) {
            return null;
        }
        AiArtifactConfirm record = confirmMapper.selectByTaskAndKey(context.task().getId(), artifactKey);
        if (record == null || record.getAdoptedRef() == null) {
            return null;
        }
        if (!Constants.AiArtifactAction.ADOPTED.equals(record.getAction())
                && !Constants.AiArtifactAction.ADOPTED_EDITED.equals(record.getAction())) {
            return null;
        }
        Object value = record.getAdoptedRef().get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 派生边来源：编辑后产物的 sourceRefs，缺省回落原产物；只保留提交范围内的需求（3.5） */
    private List<UUID> sourceRequirementIds(AdoptContext context, Map<String, Object> content) {
        Object raw = content.get("sourceRefs") != null ? content.get("sourceRefs")
                : contentOf(context.artifact()).get("sourceRefs");
        Set<UUID> scope = scopeRequirementIds(context);
        Set<UUID> sources = new java.util.LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> ref)) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(String.valueOf(ref.get("requirementId")).trim());
                    if (scope.isEmpty() || scope.contains(id)) {
                        sources.add(id);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 编辑注入的非法需求 ID 不建边
                }
            }
        }
        return new ArrayList<>(sources);
    }

    private static Set<UUID> scopeRequirementIds(AdoptContext context) {
        Object raw = context.task().getInput() == null ? null
                : context.task().getInput().get("requirementIds");
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<UUID> scope = new java.util.LinkedHashSet<>();
        for (Object element : list) {
            try {
                scope.add(UUID.fromString(String.valueOf(element)));
            } catch (IllegalArgumentException ignored) {
                // 提交入参已在发起侧校验，此处容错
            }
        }
        return scope;
    }

    /** 采纳回执：adopted_ref 存相关键，改名采纳时附 editedDiff（4.3④ 审计留痕，无 DDL） */
    private static AdoptOutcome outcome(AdoptContext context, UUID createdId, String refKey, String refValue) {
        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put(refKey, refValue);
        return finish(context, createdId, adoptedRef);
    }

    private static AdoptOutcome outcome(AdoptContext context, UUID createdId, Map<String, Object> adoptedRef) {
        return finish(context, createdId, adoptedRef);
    }

    private static AdoptOutcome finish(AdoptContext context, UUID createdId, Map<String, Object> adoptedRef) {
        if (Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())) {
            Map<String, Object> diff = editedDiff(contentOf(context.artifact()), resolveContent(context));
            if (!diff.isEmpty()) {
                adoptedRef.put("editedDiff", diff);
            }
        }
        return new AdoptOutcome(createdId, adoptedRef);
    }

    private static Map<String, Object> editedDiff(Map<String, Object> original, Map<String, Object> resolved) {
        Map<String, Object> diff = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : resolved.entrySet()) {
            if (Objects.equals(original.get(entry.getKey()), entry.getValue())) {
                continue;
            }
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("from", original.get(entry.getKey()));
            pair.put("to", entry.getValue());
            diff.put(entry.getKey(), pair);
        }
        return diff;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asAttributes(Object raw) {
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> trimToNull(asString(item)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
