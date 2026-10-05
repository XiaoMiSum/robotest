package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import xyz.migoo.framework.common.util.JsonUtils;
import io.github.xiaomisum.robotest.model.dto.request.plan.TestPlanCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.review.TestReviewCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewDetailRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.trimToNull;

/**
 * 圈选建议采纳支撑（生成链详设 3.5 圈选类型）：review:create / plan:create 资源权限
 * （1000018208）→ target.createParams 整包转换为既有创建请求（1000018206）→
 * caseIds 全量属于当前项目（1000018207）→ 委托既有 createReview / createPlan 落库，
 * 快照与 case_snapshot 边由既有创建流程内建。
 */
@Service
public class SelectionAdoptSupport {

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestReviewService testReviewService;
    @Resource
    private TestPlanService testPlanService;

    /**
     * @param createPermission 落库资源权限（review:create / plan:create）
     * @param planSide         true = 计划（name 入参），false = 评审（title 入参）
     */
    public AdoptOutcome create(AdoptContext context, String createPermission, boolean planSide) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        LoginUser loginUser = context.loginUser();
        if (loginUser == null || !loginUser.getPermissions().contains(createPermission)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.GENERATION_NO_ADOPT_PERMISSION);
        }
        Map<String, Object> params = context.createParams();
        if (params == null || params.isEmpty()) {
            // 调整后采纳必须整体提交 target.createParams（详设 3.6）
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        return planSide ? createPlan(context, params) : createReview(context, params);
    }

    private AdoptOutcome createReview(AdoptContext context, Map<String, Object> params) {
        TestReviewCreateReqDTO reqDTO = convert(params, TestReviewCreateReqDTO.class);
        if (reqDTO == null || trimToNull(reqDTO.getTitle()) == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        Set<UUID> caseIds = validateSelection(context.projectId(), reqDTO.getSelectedNodes(),
                TestReviewCreateReqDTO.SelectedNode::getDocumentId,
                TestReviewCreateReqDTO.SelectedNode::getCaseIds);
        TestReviewDetailRespDTO created = testReviewService.createReview(
                context.projectId(), context.operatorId(), reqDTO);
        return new AdoptOutcome(created.getId(),
                Map.of("reviewId", String.valueOf(created.getId()), "caseCount", caseIds.size()));
    }

    private AdoptOutcome createPlan(AdoptContext context, Map<String, Object> params) {
        TestPlanCreateReqDTO reqDTO = convert(params, TestPlanCreateReqDTO.class);
        if (reqDTO == null || trimToNull(reqDTO.getName()) == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        Set<UUID> caseIds = validateSelection(context.projectId(), reqDTO.getSelectedNodes(),
                TestPlanCreateReqDTO.SelectedNode::getDocumentId,
                TestPlanCreateReqDTO.SelectedNode::getCaseIds);
        TestPlanDetailRespDTO created = testPlanService.createPlan(
                context.projectId(), context.operatorId(), reqDTO);
        return new AdoptOutcome(created.getId(),
                Map.of("planId", String.valueOf(created.getId()), "caseCount", caseIds.size()));
    }

    private <T> T convert(Map<String, Object> params, Class<T> type) {
        try {
            // 序列化回环转换（SceneStepUtil 同款口径）：类型不匹配按圈选输入非法处理
            return JsonUtils.parseObject(JsonUtils.toJsonString(params), type);
        } catch (RuntimeException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
    }

    /** 结构校验 + caseIds 归属校验（详设 6：caseIds 不存在于当前项目 → 1000018207） */
    private <T> Set<UUID> validateSelection(UUID projectId, List<T> selectedNodes,
            Function<T, UUID> documentId, Function<T, List<UUID>> caseIds) {
        if (selectedNodes == null || selectedNodes.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (T node : selectedNodes) {
            if (documentId.apply(node) == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
            }
            List<UUID> nodeCases = caseIds.apply(node);
            if (nodeCases != null) {
                ids.addAll(nodeCases);
            }
        }
        if (ids.isEmpty()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_INPUT_INVALID);
        }
        validateCases(projectId, ids);
        return ids;
    }

    private void validateCases(UUID projectId, Set<UUID> caseIds) {
        Map<UUID, TestCaseNode> nodes = testCaseNodeMapper.listByIds(caseIds).stream()
                .collect(Collectors.toMap(TestCaseNode::getId, item -> item, (left, right) -> left));
        Set<UUID> documentIds = nodes.values().stream()
                .map(TestCaseNode::getDocumentId)
                .collect(Collectors.toSet());
        Set<UUID> ownedDocuments = testCaseDocumentMapper.listByIds(documentIds).stream()
                .filter(doc -> projectId.equals(doc.getProjectId()))
                .map(TestCaseDocument::getId)
                .collect(Collectors.toSet());
        for (UUID id : caseIds) {
            TestCaseNode node = nodes.get(id);
            if (node == null || !Constants.NodeType.CASE.equals(node.getType())
                    || !ownedDocuments.contains(node.getDocumentId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.SELECTION_CASE_NOT_FOUND);
            }
        }
    }
}
