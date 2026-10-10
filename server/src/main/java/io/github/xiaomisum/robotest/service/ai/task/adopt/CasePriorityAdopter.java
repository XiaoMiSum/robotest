package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.ai.task.handler.CasePriorityHandler;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.nvl;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 用例级别推荐承接（详设 3.3 落库 / 4.2 覆盖口径）：
 * 仅在建议级别与现状不同时写库（无变化即无写入），已有人工设定的级别不被批量采纳改写。
 */
@Service
public class CasePriorityAdopter implements ArtifactAdopter {

    /** 补全与级别推荐均要求脑图编辑权（详设 3.6.1「落库承接」列） */
    private static final String PERMISSION = "case:edit";

    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

    @Resource
    private AssistAdoptSupport support;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    @Override
    public String type() {
        return CasePriorityHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        support.requirePermission(context.loginUser(), PERMISSION);
        AssistAdoptSupport.CaseTarget target = support.resolveCaseTarget(context);
        Map<String, Object> content = contentOf(context.artifact());

        String current = CaseAssistSupport.displayPriority(target.node().getPriority());
        String suggested = suggested(context, content);
        if (suggested != null && apply(target, current, suggested)) {
            support.record(context, "TEST_CASE_NODE", target.node().getId(),
                    nvl(target.node().getTitle()), "CASE_UPDATED",
                    "采纳 AI 级别推荐「" + nvl(target.node().getTitle()) + "」：" + current + " → " + suggested);
        }

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("nodeId", String.valueOf(target.node().getId()));
        adoptedRef.put("from", current);
        adoptedRef.put("to", suggested);
        return new AdoptOutcome(target.node().getId(), adoptedRef);
    }

    /** 建议级别与现状一致时不写库（详设 4.2 无变化即无写入）；返回是否实际落库 */
    private boolean apply(AssistAdoptSupport.CaseTarget target, String current, String suggested) {
        if (!PRIORITIES.contains(suggested)) {
            // 产物侧已按 PRIORITIES 清洗，走到这里只能是编辑载荷畸形（1000018115）
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        if (suggested.equals(current)) {
            return false;
        }
        TestCaseNode carrier = new TestCaseNode();
        carrier.setId(target.node().getId());
        carrier.setPriority(CaseAssistSupport.platformPriority(suggested));
        testCaseNodeMapper.updateById(carrier);
        return true;
    }

    /** 建议级别：adopted_edited 以编辑载荷为准（人工显式采纳），否则取产物；空白视为无建议 */
    private static String suggested(AdoptContext context, Map<String, Object> content) {
        Object raw = content.get("suggested");
        if (Constants.AiArtifactAction.ADOPTED_EDITED.equals(context.action())
                && context.content() != null && context.content().containsKey("suggested")) {
            raw = context.content().get("suggested");
        }
        return trimToNull(asString(raw));
    }
}
