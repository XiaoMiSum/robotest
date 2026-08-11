package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
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

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;

    /**
     * 校验 userId 是否为 projectId 对应项目所在工作空间的成员；不满足抛业务异常。
     */
    public void requireProjectMember(UUID projectId, UUID userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
        }
        if (!workspaceUserMapper.existsByWorkspaceIdAndUserId(project.getWorkspaceId(), userId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
    }

    /**
     * WS 场景校验（返回布尔而非抛异常，供握手/加入房间前静默拒绝）：
     * docId → test_case_module(type=document) → ws_project → ws_user 成员。
     * userId 为字符串（WS 会话属性 USER_ID），非法格式视为无权限。
     */
    public boolean isDocumentMember(UUID docId, String userId) {
        if (docId == null || userId == null) {
            return false;
        }

        TestCaseModule document = testCaseModuleMapper.selectById(docId);
        if (document == null || !Constants.ModuleType.DOCUMENT.equals(document.getType())) {
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
