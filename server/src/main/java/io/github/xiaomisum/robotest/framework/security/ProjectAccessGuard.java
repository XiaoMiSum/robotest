package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * 项目级授权守卫（docs/00-spec/40-security/01-security.md 第 14 行：项目内操作另需 X-Active-Project 头，验证项目归属）。
 *
 * <p>校验链路：projectId → ws_project.workspaceId → ws_user 是否存在该成员。
 * 任一环节缺失即视为无权限；文档级校验（成员 / case:edit）为 WS 连接与可写帧的统一判定口径。</p>
 */
@Component
public class ProjectAccessGuard {

    /**
     * 系统操作者特殊值（全零 UUID）：定时任务等后台链路以系统身份执行，
     * 不归属任何真实用户；成员校验对该值直通放行。
     */
    public static final UUID SYSTEM_OPERATOR_ID = new UUID(0L, 0L);

    /** 文档编辑权限码（sys_permission.code = 'case:edit'，见 v1.sql） */
    private static final String PERMISSION_CASE_EDIT = "case:edit";

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;

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
        return findDocumentMember(docId, userId) != null;
    }

    /**
     * WS 可写帧转发与持久化前的编辑权限校验（安全规范 §4「对可写消息执行独立的业务权限校验」、
     * 实时协议 78 号 6.1 写权限行）：在成员链路之上追加 sys_role.permissions 含 case:edit，
     * 任一环节缺失即无权限（fail-closed）。WS 长连接权限可能在连接期间被撤销，
     * 每次写操作前重查、不做缓存。
     */
    public boolean hasDocumentEditPermission(UUID docId, String userId) {
        WorkspaceUser member = findDocumentMember(docId, userId);
        if (member == null || member.getWorkspaceRole() == null) {
            return false;
        }
        SysRole role = sysRoleMapper.selectById(member.getWorkspaceRole());
        return role != null && role.getPermissions() != null && role.getPermissions().contains(PERMISSION_CASE_EDIT);
    }

    /**
     * 文档成员解析：doc → project → workspace 成员行，任一环节缺失返回 null（fail-closed）。
     * userId 为字符串（WS 会话属性 USER_ID），非法格式视为非成员。
     */
    private WorkspaceUser findDocumentMember(UUID docId, String userId) {
        if (docId == null || userId == null) {
            return null;
        }

        TestCaseDocument document = testCaseDocumentMapper.selectById(docId);
        if (document == null) {
            return null;
        }

        Project project = projectMapper.selectById(document.getProjectId());
        if (project == null) {
            return null;
        }

        UUID userIdUuid;
        try {
            userIdUuid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), userIdUuid);
    }
}
