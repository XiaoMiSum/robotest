package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

public interface ProjectMapper extends BaseMapperX<Project> {

    default long countByWorkspaceId(UUID workspaceId) {
        return selectCount(Project::getWorkspaceId, workspaceId);
    }

    /** 全量项目数（数据概览，含归档态） */
    default long countAll() {
        Long cnt = selectCount();
        return cnt == null ? 0 : cnt;
    }

    /** 近 N 日新增项目数（数据概览脚注口径：created_at >= since） */
    default long countCreatedSince(LocalDateTime since) {
        Long cnt = selectCount(new LambdaQueryWrapperX<Project>().ge(Project::getCreatedAt, since));
        return cnt == null ? 0 : cnt;
    }

    default List<Project> listByIds(Collection<UUID> ids) {
        return selectList(new LambdaQueryWrapperX<Project>().in(Project::getId, ids));
    }

    default List<Project> listByWorkspaceId(UUID workspaceId) {
        return selectList(new LambdaQueryWrapperX<Project>().eq(Project::getWorkspaceId, workspaceId));
    }

    default PageResult<Project> findPage(PageParam pageParam, UUID workspaceId,
                                          String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .likeIfPresent(Project::getName, keyword)
                .eqIfPresent(Project::getStatus, status)
                .orderByDesc(Project::getCreatedAt));
    }

    default Project findByName(UUID workspaceId, String name) {
        return selectOne(new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .eq(Project::getName, name));
    }

    default Project findByNameExcludingId(UUID workspaceId, String name, UUID excludeId) {
        return selectOne(new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .eq(Project::getName, name)
                .ne(Project::getId, excludeId));
    }

    /** 全量项目列表（向量重建/补偿按项目分组扫描用） */
    default List<Project> listAll() {
        return selectList(new LambdaQueryWrapperX<Project>());
    }
}
