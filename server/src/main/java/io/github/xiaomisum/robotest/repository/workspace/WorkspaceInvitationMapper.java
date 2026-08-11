package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceInvitation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
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

    /**
     * 原子自增使用次数：并发 join 时通过 SQL 条件保证不超出 maxUses（上限校验无法靠读-改-写完成）。
     */
    @Update("UPDATE ws_invitation SET use_count = use_count + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = FALSE AND (max_uses IS NULL OR use_count < max_uses)")
    int incrementUseCount(@Param("id") UUID id);
}
