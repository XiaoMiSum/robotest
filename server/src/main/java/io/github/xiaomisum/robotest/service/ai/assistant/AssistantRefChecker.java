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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 助手实体引用校验（详设 3.3 context / 3.5 attachments / 4.4 跨工作空间指代）：类型白名单取
 * 追溯节点类型，逻辑外键沿用各实体 projectId → 项目 → 工作空间的既有归属链解析；
 * 不存在与越权同码，不经由错误码泄露对象存在性。
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
        UUID projectId = projectIdOf(entityType, entityId);
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project == null ? null : project.getWorkspaceId();
    }

    /**
     * 引用实体所属项目；类型不受支持、实体不存在或已删除返回 null。
     * 用例节点不带项目字段，经所属文档的项目归属判定。
     */
    public UUID projectIdOf(String entityType, UUID entityId) {
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
        return projectId;
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

    /** 附件引用须存在且属当前活跃工作空间（详设 3.5 校验），否则 1000018262 */
    public void requireInActiveWorkspace(String entityType, UUID entityId, UUID activeWorkspaceId) {
        UUID workspaceId = workspaceOf(entityType, entityId);
        if (workspaceId == null || !workspaceId.equals(activeWorkspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID);
        }
    }

    /** 发送侧成员校验（详设 3.1 发送消息附带 X-Active-Workspace）：非成员按 1000018261 拒绝 */
    public void requireWorkspaceMember(UUID workspaceId, UUID userId) {
        if (workspaceId == null || userId == null
                || !workspaceUserMapper.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
    }

    /** 解析出的单个业务引用须存在且属活跃工作空间（详设 4.4），否则 1000018261；不存在与越权同码 */
    public void requireActiveWorkspaceRef(UUID activeWorkspaceId, String entityType, UUID entityId) {
        UUID workspaceId = workspaceOf(entityType, entityId);
        if (workspaceId == null || !workspaceId.equals(activeWorkspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
    }

    /** 解析出的项目引用须存在且属活跃工作空间（详设 4.4），否则 1000018261；不存在与越权同码 */
    public void requireActiveWorkspaceProject(UUID activeWorkspaceId, UUID projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !activeWorkspaceId.equals(project.getWorkspaceId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
    }

    /**
     * 批量用例引用校验（详设 4.4）：一次取回用例 → 文档 → 项目链，任一缺失或越权按 1000018261 拒绝。
     * 入参须为去重集合，行数不足即判定为幻觉或已删除引用；校验通过返回用例链解析出的项目集。
     */
    public Set<UUID> requireActiveWorkspaceCases(UUID activeWorkspaceId, Collection<UUID> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return Set.of();
        }
        List<TestCaseNode> rows = testCaseNodeMapper.listByIds(caseIds);
        if (rows.size() != caseIds.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
        Set<UUID> documentIds = rows.stream()
                .map(TestCaseNode::getDocumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (documentIds.size() != rows.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
        List<TestCaseDocument> documents = testCaseDocumentMapper.listByIds(documentIds);
        if (documents.size() != documentIds.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
        Set<UUID> projectIds = documents.stream()
                .map(TestCaseDocument::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (projectIds.size() != documents.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
        List<Project> projects = projectMapper.listByIds(projectIds);
        if (projects.size() != projectIds.size()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
        }
        for (Project project : projects) {
            if (!activeWorkspaceId.equals(project.getWorkspaceId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE);
            }
        }
        return projectIds;
    }

    /** 引用展示标题（citations / 预览目标名）：需求优先取编号，其余取名称字段；缺失返回 null */
    public String titleOf(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) {
            return null;
        }
        return switch (entityType) {
            case Constants.TraceNodeType.REQUIREMENT -> {
                Requirement row = requirementMapper.selectById(entityId);
                yield row == null ? null
                        : (row.getCode() == null || row.getCode().isBlank() ? row.getTitle() : row.getCode());
            }
            case Constants.TraceNodeType.MODULE -> {
                ProjectModule row = projectModuleMapper.selectById(entityId);
                yield row == null ? null : row.getName();
            }
            case Constants.TraceNodeType.MINDMAP_DOCUMENT -> {
                TestCaseDocument row = testCaseDocumentMapper.selectById(entityId);
                yield row == null ? null : row.getName();
            }
            case Constants.TraceNodeType.TEST_CASE -> {
                TestCaseNode row = testCaseNodeMapper.selectById(entityId);
                yield row == null ? null : row.getTitle();
            }
            case Constants.TraceNodeType.TEST_REVIEW -> {
                TestReview row = testReviewMapper.selectById(entityId);
                yield row == null ? null : row.getTitle();
            }
            case Constants.TraceNodeType.TEST_PLAN -> {
                TestPlan row = testPlanMapper.selectById(entityId);
                yield row == null ? null : row.getName();
            }
            default -> null;
        };
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
