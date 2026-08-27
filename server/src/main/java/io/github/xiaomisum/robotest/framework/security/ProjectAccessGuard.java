package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * 项目级授权守卫（security.md 第 14 行：项目内操作另需 X-Active-Project 头，验证项目归属）。
 *
 * <p>校验链路：projectId → ws_project.workspaceId → ws_user 是否存在该成员。
 * 任一环节缺失即视为无权限，判定口径与 DocumentPersistenceHandler.hasCaseEditPermission 一致。</p>
 */
@Component
public class ProjectAccessGuard {

    /**
     * 系统操作者特殊值（全零 UUID）：定时任务等后台链路以系统身份执行，
     * 不归属任何真实用户；成员校验对该值直通放行。
     */
    public static final UUID SYSTEM_OPERATOR_ID = new UUID(0L, 0L);

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;

    /**
     * 校验 userId 是否为 projectId 对应项目所在工作空间的成员；不满足抛业务异常。
     * 系统身份（定时任务调度）直通，不做成员校验。
     */
    public void requireProjectMember(UUID projectId, UUID userId) {
        if (SYSTEM_OPERATOR_ID.equals(userId)) {
            return;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }
        if (!workspaceUserMapper.existsByWorkspaceIdAndUserId(project.getWorkspaceId(), userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    /**
     * 成员校验（工作空间上下文重载）：projectId 必须归属于 workspaceId（X-Active-Workspace 头），
     * 且 userId 为该空间成员。
     *
     * <p>workspaceId 以请求头为准而非 project 行反查，故必须先校验 project.workspaceId 与之一致，
     * 防止携带自己为管理员的其它空间头跨空间越权；归属不符按项目不存在处理（不泄露跨空间项目存在性）。
     * 读写分级授权由 Controller 层 @PreAuthorize 权限码承担，本守卫只做归属一致性兜底。</p>
     */
    public void requireProjectMember(UUID projectId, UUID workspaceId, UUID userId) {
        if (SYSTEM_OPERATOR_ID.equals(userId)) {
            return;
        }
        requireWorkspaceProject(projectId, workspaceId);
        if (!workspaceUserMapper.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    private Project requireWorkspaceProject(UUID projectId, UUID workspaceId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getWorkspaceId().equals(workspaceId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }
        return project;
    }

    /**
     * WS 场景校验（返回布尔而非抛异常，供握手/加入房间前静默拒绝）：
     * docId → test_case_document → ws_project → ws_user 成员。
     * userId 为字符串（WS 会话属性 USER_ID），非法格式视为无权限。
     */
    public boolean isDocumentMember(UUID docId, String userId) {
        if (docId == null || userId == null) {
            return false;
        }

        TestCaseDocument document = testCaseDocumentMapper.selectById(docId);
        if (document == null) {
            return false;
        }

        Project project = projectMapper.selectById(document.getProjectId());
        if (project == null) {
            return false;
        }

        UUID userIdUuid;
        try {
            userIdUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return workspaceUserMapper.existsByWorkspaceIdAndUserId(project.getWorkspaceId(), userIdUuid);
    }
}
