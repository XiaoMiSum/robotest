package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectStatusCountsRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
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

    @Select("""
            <script>
            SELECT
                COALESCE(SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END), 0) AS active,
                COALESCE(SUM(CASE WHEN status = 'archived' THEN 1 ELSE 0 END), 0) AS archived
            FROM ws_project
            WHERE workspace_id = #{workspaceId}
              AND is_deleted = FALSE
            <if test="keyword != null and keyword != ''">
              AND (
                LOWER(COALESCE(name, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(COALESCE(description, '')) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            </if>
            </script>
            """)
    @Results(id = "projectStatusCounts",
            value = {
                    @Result(column = "active", property = "active"),
                    @Result(column = "archived", property = "archived")
            })
    ProjectStatusCountsRespDTO countStatusByWorkspaceId(@Param("workspaceId") UUID workspaceId,
                                                      @Param("keyword") String keyword);

    default PageResult<Project> findPage(PageParam pageParam, UUID workspaceId,
                                          String keyword, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<Project>()
                .eq(Project::getWorkspaceId, workspaceId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Project::getName, keyword)
                        .or()
                        .like(Project::getDescription, keyword))
                .eqIfPresent(Project::getStatus, status)
                .orderByDesc(Project::getUpdatedAt)
                .orderByDesc(Project::getId));
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
