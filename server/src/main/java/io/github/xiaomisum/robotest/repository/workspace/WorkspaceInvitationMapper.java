package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface WorkspaceInvitationMapper extends BaseMapperX<WorkspaceInvitation> {

    default PageResult<WorkspaceInvitation> findPageByWorkspaceId(PageParam pageParam, UUID workspaceId) {
        return selectPage(pageParam, new LambdaQueryWrapperX<WorkspaceInvitation>()
                .eq(WorkspaceInvitation::getWorkspaceId, workspaceId)
                .orderByDesc(WorkspaceInvitation::getCreatedAt));
    }

    default List<WorkspaceInvitation> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<WorkspaceInvitation>()
                .eq(WorkspaceInvitation::getWorkspaceId, workspaceId));
    }
}
