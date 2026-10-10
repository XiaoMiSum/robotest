package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseCompleteHandler;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import io.github.xiaomisum.robotest.service.trace.TraceEdgeWriter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例补全建议承接（详设 3.2 落库 / 4.2 覆盖口径 / 4.3 补充节点与追溯边）：
 * 字段只补空缺、编辑值按 adopted_edited 局部落库，补充节点按 target.extraNodePosition 落位。
 */
@Service
public class CaseCompleteAdopter implements ArtifactAdopter {

    /** 补全与级别推荐均要求脑图编辑权（详设 3.6.1「落库承接」列） */
    private static final String PERMISSION = "case:edit";

    /** 补充节点落位（详设 3.2 target.extraNodePosition，缺省 sibling）：child 落源节点之下 */
    private static final String POSITION_CHILD = "child";

    private static final String FIELD_PRECONDITION = "precondition";
    private static final String FIELD_TAGS = "tags";
    private static final List<String> FIELDS = List.of(FIELD_PRECONDITION, "steps", "expected", FIELD_TAGS);

    /** 新建用例节点的默认级别：与既有建用例流程缺省口径一致 */
    private static final String DEFAULT_PRIORITY = "P1";
    /** 标签采纳上限与产物清洗口径一致（详设 3.2 TAGS=5），避免编辑载荷绕过清洗 */
    private static final int MAX_TAGS = 5;
    private static final String VERSION_PREFIX = "v";

    @Resource
    private AssistAdoptSupport support;
    @Resource
    private TestCaseNodeService testCaseNodeService;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TraceEdgeMapper traceEdgeMapper;
    @Resource
    private TraceEdgeWriter traceEdgeWriter;
    @Resource
    private VectorIndexService vectorIndexService;

    @Override
    public String type() {
        return CaseCompleteHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        support.requirePermission(context.loginUser(), PERMISSION);
        AssistAdoptSupport.CaseTarget target = support.resolveCaseTarget(context);
        Map<String, Object> content = contentOf(context.artifact());

        boolean touched = applyFields(context, target, content);
        List<UUID> created = insertExtraNodes(context, target, content);

        if (touched || !created.isEmpty()) {
            support.record(context, "TEST_CASE_NODE", target.node().getId(), nvl(target.node().getTitle()),
                    "CASE_UPDATED", "采纳 AI 用例补全建议「" + nvl(target.node().getTitle()) + "」");
        }

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("nodeId", String.valueOf(target.node().getId()));
        adoptedRef.put("createdNodeIds", created.stream().map(UUID::toString).toList());
        return new AdoptOutcome(target.node().getId(), adoptedRef);
    }

    /**
     * 字段采纳（详设 4.2）：existing 非空不覆盖，仅 adopted_edited 携带的编辑值可改写；
     * 解析后的值与现状一致则跳过（无变化即无写入）。
     * 属性子节点复用既有 updateCaseFields（白名单不含 tags，标签走部分更新载体），
     * 且该入口内建成员与归属二次校验，空变更列表时仅校验不写。
     */
    private boolean applyFields(AdoptContext context, AssistAdoptSupport.CaseTarget target,
            Map<String, Object> content) {
        // existing 取库内现状而非产物快照：任务执行后节点可能已被人工改动，避免覆盖后填内容
        CaseAssistSupport.ExistingFields live = CaseAssistSupport.existingFields(target.node(), target.resolved());
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put(FIELD_PRECONDITION, live.precondition());
        existing.put("steps", live.steps());
        existing.put("expected", live.expected());
        existing.put(FIELD_TAGS, live.tags());
        Map<String, Object> pairs = asMap(content.get("fields"));
        Map<String, Object> edited = editedFields(context);
        List<Map<String, Object>> changes = new ArrayList<>(FIELDS.size());
        List<String> tags = null;
        for (String field : FIELDS) {
            Object current = existing.get(field);
            Object suggested = asMap(pairs.get(field)).get("suggested");
            Object resolved = edited.containsKey(field) ? edited.get(field)
                    : (isEmpty(current) ? suggested : current);
            if (FIELD_TAGS.equals(field)) {
                List<String> next = asTags(resolved);
                if (!sameValue(current, next)) {
                    tags = next;
                }
                continue;
            }
            Object normalized = normalize(field, resolved);
            if (sameValue(current, normalized)) {
                continue;
            }
            changes.add(change(field, normalized));
        }

        testCaseNodeService.updateCaseFields(context.projectId(), context.operatorId(),
                target.node().getId(), changes);
        if (tags == null) {
            return !changes.isEmpty();
        }
        TestCaseNode carrier = new TestCaseNode();
        carrier.setId(target.node().getId());
        carrier.setTags(tags);
        testCaseNodeMapper.updateById(carrier);
        return true;
    }

    /**
     * 补充节点（详设 4.3）：结构节点不建边不建用例实体；用例实体 direct insert（不走既有建用例入口，
     * 免逐节点重嵌），批内统一一次 upsertTestCase，并继承源节点的上游派生边。
     */
    private List<UUID> insertExtraNodes(AdoptContext context, AssistAdoptSupport.CaseTarget target,
            Map<String, Object> content) {
        Object rawExtras = effectiveExtras(context, content);
        if (!(rawExtras instanceof List<?> extras) || extras.isEmpty()) {
            return List.of();
        }
        UUID documentId = target.document().getId();
        UUID sourceId = target.node().getId();
        UUID parentId = resolveParent(context, target.node());
        int order = testCaseNodeMapper.listByParentId(parentId).size();
        List<UUID> created = new ArrayList<>(extras.size());
        for (Object element : extras) {
            Map<String, Object> extra = asMap(element);
            String title = trimToNull(asString(extra.get("title")));
            if (title == null) {
                continue;
            }
            boolean isTestCase = Boolean.parseBoolean(asString(extra.get("isTestCase")));
            TestCaseNode node = new TestCaseNode();
            node.setDocumentId(documentId);
            node.setParentId(parentId);
            node.setType(isTestCase ? Constants.NodeType.CASE : Constants.NodeType.NORMAL);
            node.setTitle(title);
            node.setSortOrder(order++);
            node.setVersion(1);
            node.setAiGenerated(true);
            if (isTestCase) {
                node.setPriority(DEFAULT_PRIORITY);
            }
            testCaseNodeMapper.insert(node);
            created.add(node.getId());
            if (isTestCase) {
                traceEdgeWriter.writeAiDerivationEdges(context.projectId(), upstreamOf(sourceId),
                        Constants.TraceNodeType.TEST_CASE, node.getId(), VERSION_PREFIX + 1);
            }
        }
        if (!created.isEmpty()) {
            vectorIndexService.upsertTestCase(documentId, context.operatorId());
        }
        return created;
    }

    /** 补充节点清单：默认随产物采纳，adopted_edited 显式携带时以编辑载荷为准（可剔除） */
    private static Object effectiveExtras(AdoptContext context, Map<String, Object> content) {
        if (Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())
                && context.content() != null && context.content().containsKey("extraNodes")) {
            return context.content().get("extraNodes");
        }
        return content.get("extraNodes");
    }

    /** 补充节点落位：child 落源节点之下，缺省 sibling 落源节点同级 */
    private static UUID resolveParent(AdoptContext context, TestCaseNode source) {
        if (POSITION_CHILD.equals(context.extraNodePosition())) {
            return source.getId();
        }
        // 源节点无父级（根下用例）时回退为 child，避免补充节点脱离文档树
        return source.getParentId() != null ? source.getParentId() : source.getId();
    }

    /** 源节点的上游需求（derivation 边 source 端）；补充节点据此继承边，不改动源节点既有边 */
    private List<UUID> upstreamOf(UUID sourceNodeId) {
        return traceEdgeMapper.listActiveByTarget(Constants.TraceNodeType.TEST_CASE, List.of(sourceNodeId)).stream()
                .filter(edge -> Constants.TraceEdgeType.DERIVATION.equals(edge.getEdgeType())
                        && Constants.TraceNodeType.REQUIREMENT.equals(edge.getSourceType()))
                .map(TraceEdge::getSourceId)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Map<String, Object> editedFields(AdoptContext context) {
        if (!Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())
                || context.content() == null) {
            return Map.of();
        }
        return asMap(context.content().get("fields"));
    }

    /** 编辑载荷的字段值按类型归一；steps/expected 非列表视为畸形输入（1000018115），避免整列被清空 */
    private static Object normalize(String field, Object value) {
        if (value == null) {
            return null;
        }
        if (FIELD_PRECONDITION.equals(field)) {
            return value instanceof List<?> list
                    ? (list.isEmpty() ? null : trimToNull(asString(list.get(0))))
                    : trimToNull(asString(value));
        }
        if (!(value instanceof List<?>)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        return value;
    }

    /** 标签按产物清洗同口径归一：单串视作单标签，列表按上限截断，空串与空串等价 */
    private static List<String> asTags(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text.trim());
        }
        return stringList(value, MAX_TAGS);
    }

    private static Map<String, Object> change(String field, Object value) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("field", field);
        change.put("op", "replace");
        change.put("value", value);
        return change;
    }

    private static boolean sameValue(Object existing, Object resolved) {
        if (isEmpty(existing) && isEmpty(resolved)) {
            return true;
        }
        return Objects.equals(existing, resolved);
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || (value instanceof String text && text.isBlank())
                || (value instanceof Collection<?> values && values.isEmpty());
    }
}
