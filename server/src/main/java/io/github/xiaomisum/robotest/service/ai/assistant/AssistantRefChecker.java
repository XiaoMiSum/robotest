package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * 助手实体引用校验（详设 3.3 context / 3.5 attachments）：类型白名单取追溯节点类型，
 * 逻辑外键沿用各实体 projectId → 项目 → 工作空间的既有归属链解析。
 */
@Component
public class AssistantRefChecker {

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;

    /**
     * 引用实体所属工作空间；类型不受支持、实体不存在或已删除返回 null（查重不泄露存在性）。
     */
    public UUID workspaceOf(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }
        UUID projectId = switch (entityType) {
            case Constants.TraceNodeType.REQUIREMENT -> {
                Requirement item = requirementMapper.selectById(entityId);
                yield item == null ? null : item.getProjectId();
            }
            case Constants.TraceNodeType.MODULE -> {
                ProjectModule item = projectModuleMapper.selectById(entityId);
                yield item == null ? null : item.getProjectId();
            }
            case Constants.TraceNodeType.MINDMAP_DOCUMENT -> {
                TestCaseDocument item = testCaseDocumentMapper.selectById(entityId);
                yield item == null ? null : item.getProjectId();
            }
            case Constants.TraceNodeType.TEST_CASE -> {
                TestCaseNode item = testCaseNodeMapper.selectById(entityId);
                yield item == null ? null : documentProjectId(item.getDocumentId());
            }
            case Constants.TraceNodeType.TEST_REVIEW -> {
                TestReview item = testReviewMapper.selectById(entityId);
                yield item == null ? null : item.getProjectId();
            }
            case Constants.TraceNodeType.TEST_PLAN -> {
                TestPlan item = testPlanMapper.selectById(entityId);
                yield item == null ? null : item.getProjectId();
            }
            default -> null;
        };
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project == null ? null : project.getWorkspaceId();
    }

    /**
     * 引用须存在且属当前用户可见（成员）工作空间（详设 3.3），否则 1000018262。
     * 不存在与越权同码，避免经引用校验探测对象存在性。
     */
    public void requireUserVisible(String entityType, UUID entityId, UUID userId) {
        UUID workspaceId = workspaceOf(entityType, entityId);
        if (workspaceId == null || !workspaceUserMapper.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID);
        }
    }

    /** 用例节点不带项目字段，经所属文档的项目归属判定 */
    private UUID documentProjectId(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        return document == null ? null : document.getProjectId();
    }
}
