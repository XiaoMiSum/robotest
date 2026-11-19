package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ProposalAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseNodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例优化建议承接（详设 3.2 落库 / 4.2 覆盖口径）：
 * 前置条件 / 步骤 / 预期结果按建议值整体替换（删旧属性子节点再插新节点），标签只补空缺；
 * 编辑值按 adopted_edited 局部落库；优先级随同一条提案应用，
 * 级别变化项写级别列，级别一致项不写列但同样写用例修改记录（from = to）。
 */
@Service
public class CaseOptimizeAdopter implements ProposalAdopter {

    private static final String FIELD_PRECONDITION = "precondition";
    private static final String FIELD_TAGS = "tags";
    private static final List<String> FIELDS = List.of(FIELD_PRECONDITION, "steps", "expected", FIELD_TAGS);

    /** 标签采纳上限与产物清洗口径一致（详设 3.2 TAGS=5），避免编辑载荷绕过清洗 */
    private static final int MAX_TAGS = 5;
    private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");

    @Resource
    private AssistAdoptSupport support;
    @Resource
    private TestCaseNodeService testCaseNodeService;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    @Override
    public String type() {
        return Constants.AiTaskType.CASE_OPTIMIZE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiProposalAction.REJECTED.equals(context.action())) {
            return null;
        }
        AssistAdoptSupport.CaseTarget target = support.resolveCaseTarget(context);
        Map<String, Object> content = context.proposalContent();

        String current = nvl(target.node().getPriority());
        String suggested = suggestedPriority(context, content);
        // 先校验再落库：畸形编辑载荷在写入前拒绝，避免字段已改而级别失败
        if (suggested != null && !PRIORITIES.contains(suggested)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        boolean fieldsTouched = applyFields(context, target, content);
        String detail = null;
        if (suggested != null) {
            boolean changed = applyPriority(target, suggested);
            detail = changed ? current + " → " + suggested : current + "（from = to，级别未变化）";
        }

        if (fieldsTouched || suggested != null) {
            support.record(context, "TEST_CASE_NODE", target.node().getId(), nvl(target.node().getTitle()),
                    "CASE_UPDATED", "采纳 AI 用例优化建议「" + nvl(target.node().getTitle()) + "」"
                            + (detail == null ? "" : "：" + detail));
        }

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("nodeId", String.valueOf(target.node().getId()));
        adoptedRef.put("from", current);
        adoptedRef.put("to", suggested);
        return new AdoptOutcome(target.node().getId(), adoptedRef);
    }

    /**
     * 字段采纳（详设 4.2）：前置条件 / 步骤 / 预期结果以建议值整体替换，仅现状一致或建议为空时跳过，
     * 避免无谓的属性子节点重建；标签维持仅补空缺，编辑值优先。
     * 属性子节点复用既有 updateCaseFields 的 replace 语义删旧插新（白名单不含 tags，标签走部分更新载体），
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
            Object resolved;
            if (edited.containsKey(field)) {
                resolved = edited.get(field);
            } else if (FIELD_TAGS.equals(field)) {
                resolved = isEmpty(current) ? suggested : current;
            } else {
                resolved = isEmpty(suggested) ? current : suggested;
            }
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

    /** 建议级别：adopted_edited 以编辑载荷为准（人工显式采纳），否则取产物；空白视为无建议 */
    private static String suggestedPriority(AdoptContext context, Map<String, Object> content) {
        if (Constants.AiProposalAction.ADOPTED_EDITED.equals(context.action())
                && context.content() != null && context.content().containsKey("priority")) {
            return trimToNull(asString(context.content().get("priority")));
        }
        return trimToNull(asString(asMap(content.get("priority")).get("suggested")));
    }

    /** 建议级别与现状一致时不写级别列，留痕由调用方负责（详设 3.2）；返回是否实际落库 */
    private boolean applyPriority(AssistAdoptSupport.CaseTarget target, String suggested) {
        if (suggested.equals(nvl(target.node().getPriority()))) {
            return false;
        }
        TestCaseNode carrier = new TestCaseNode();
        carrier.setId(target.node().getId());
        carrier.setPriority(suggested);
        testCaseNodeMapper.updateById(carrier);
        return true;
    }

    private static Map<String, Object> editedFields(AdoptContext context) {
        if (!Constants.AiProposalAction.ADOPTED_EDITED.equals(context.action())
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
