package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.WorkspaceInvitation;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface WorkspaceInvitationMapper extends BaseMapperX<WorkspaceInvitation> {

    default List<WorkspaceInvitation> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<WorkspaceInvitation>()
                .eq(WorkspaceInvitation::getWorkspaceId, workspaceId));
    }
}
