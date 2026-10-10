package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import io.github.xiaomisum.robotest.service.project.ProjectActivityService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.contentOf;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.parseUuid;

/**
 * 辅助功能两承接共用支撑：落库资源权限（1000018306）、承接目标解析（target 缺省回退任务入参，
 * 任务详情页发起不带 target）与采纳后的项目动态留痕。
 */
@Component
public class AssistAdoptSupport {

    @Resource
    private CaseAssistSupport assistSupport;
    @Resource
    private ProjectAccessGuard projectAccessGuard;
    @Resource
    private ProjectActivityService projectActivityService;

    /** 承接目标：文档 + 归属与类型校验后的源用例节点；resolved 保留文档全量节点索引供属性子节点读取 */
    public record CaseTarget(TestCaseDocument document, TestCaseNode node,
                             CaseAssistSupport.ResolvedCases resolved) {
    }

    /** 资源权限：确认请求必带 loginUser，缺失或未持有该权限按 1000018306 */
    public void requirePermission(LoginUser loginUser, String permission) {
        if (loginUser == null || !loginUser.getPermissions().contains(permission)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NO_PERMISSION);
        }
    }

    /**
     * 承接的用例节点：target.documentId 显式优先、缺省回退任务入参（畸形或缺失 1000018115）；
     * 节点归属与类型由 resolve 按 1000018301 / 1000018307 统一校验，随后补成员校验（SEC-014）。
     */
    public CaseTarget resolveCaseTarget(AdoptContext context) {
        UUID documentId = context.targetDocumentId() != null
                ? context.targetDocumentId()
                : CaseAssistSupport.requireDocumentId(taskInput(context));
        UUID nodeId = parseUuid(contentOf(context.artifact()).get("nodeId"));
        if (nodeId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        CaseAssistSupport.ResolvedCases resolved =
                assistSupport.resolve(context.projectId(), documentId, List.of(nodeId));
        projectAccessGuard.requireProjectMember(resolved.document().getProjectId(), context.operatorId());
        return new CaseTarget(resolved.document(), resolved.nodesById().get(nodeId), resolved);
    }

    /** 任务入参：缺失时给空表，供承接侧按 target 缺省逐字段回退 */
    public static Map<String, Object> taskInput(AdoptContext context) {
        Map<String, Object> input = context.task() == null ? null : context.task().getInput();
        return input == null ? Map.of() : input;
    }

    /** 采纳留痕：确认记录由总册确认入口写，此处补项目动态时间线 */
    public void record(AdoptContext context, String resourceType, UUID resourceId, String resourceName,
            String action, String summary) {
        projectActivityService.record(context.projectId(), context.operatorId(), resourceType, resourceId,
                resourceName, action, summary);
    }
}
