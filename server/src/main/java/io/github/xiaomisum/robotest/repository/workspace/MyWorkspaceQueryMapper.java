package io.github.xiaomisum.robotest.repository.workspace;

import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyScopeCountsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MyWorkspaceQueryMapper {

    @Results(id = "workspaceMyList",
            value = {
                    @Result(column = "id", property = "id", javaType = UUID.class, typeHandler = UUIDTypeHandler.class),
                    @Result(column = "workspace_role", property = "workspaceRole"),
                    @Result(column = "workspace_role_name", property = "workspaceRoleName"),
                    @Result(column = "name", property = "name"),
                    @Result(column = "description", property = "description"),
                    @Result(column = "default_project_id", property = "defaultProjectId",
                            javaType = UUID.class, typeHandler = UUIDTypeHandler.class),
                    @Result(column = "default_project_name", property = "defaultProjectName"),
                    @Result(column = "member_count", property = "memberCount"),
                    @Result(column = "project_count", property = "projectCount"),
                    @Result(column = "test_case_count", property = "testCaseCount"),
                    @Result(column = "status", property = "status"),
                    @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
                    @Result(column = "last_accessed_at", property = "lastAccessedAt", javaType = LocalDateTime.class)
            })
    @Select("""
            <script>
            WITH target_workspaces AS (
                SELECT
                    wu.workspace_id,
                    wu.workspace_role,
                    wu.default_project_id,
                    wu.joined_at,
                    wu.last_accessed_at,
                    w.name,
                    w.description,
                    w.status,
                    w.created_at
                FROM ws_user wu
                JOIN ws_workspace w ON w.id = wu.workspace_id
                WHERE wu.user_id = #{userId}
                  AND wu.is_deleted = FALSE
                  AND w.is_deleted = FALSE
                  AND w.status IN ('active', 'dissolved')
                <if test="keyword != null and keyword != ''">
                  AND LOWER(w.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                </if>
                <choose>
                    <when test="scope == 'managed'">
                        AND w.status = 'active'
                        AND wu.workspace_role = #{adminRoleId}
                    </when>
                    <when test="scope == 'archived'">
                        AND w.status = 'dissolved'
                    </when>
                </choose>
            ), paged_workspaces AS (
                SELECT *
                FROM target_workspaces
                ORDER BY last_accessed_at DESC NULLS LAST,
                         joined_at DESC,
                         created_at DESC,
                         workspace_id ASC
                LIMIT #{limit} OFFSET #{offset}
            ), member_stats AS (
                SELECT workspace_id, COUNT(*) AS member_count
                FROM ws_user
                WHERE is_deleted = FALSE
                  AND workspace_id IN (SELECT workspace_id FROM paged_workspaces)
                GROUP BY workspace_id
            ), project_stats AS (
                SELECT workspace_id, COUNT(*) AS project_count
                FROM ws_project
                WHERE is_deleted = FALSE
                  AND workspace_id IN (SELECT workspace_id FROM paged_workspaces)
                GROUP BY workspace_id
            ), case_stats AS (
                SELECT p.workspace_id, COUNT(n.id) AS case_count
                FROM ws_project p
                JOIN test_case_document d
                    ON d.project_id = p.id
                   AND d.is_deleted = FALSE
                JOIN test_case_node n
                    ON n.document_id = d.id
                   AND n.is_deleted = FALSE
                   AND n.type = 'case'
                WHERE p.is_deleted = FALSE
                  AND p.workspace_id IN (SELECT workspace_id FROM paged_workspaces)
                GROUP BY p.workspace_id
            )
            SELECT
                tw.workspace_id AS id,
                CAST(tw.workspace_role AS VARCHAR) AS workspace_role,
                r.name AS workspace_role_name,
                tw.name,
                tw.description,
                tw.default_project_id AS default_project_id,
                default_project.name AS default_project_name,
                COALESCE(ms.member_count, 0) AS member_count,
                COALESCE(ps.project_count, 0) AS project_count,
                COALESCE(cs.case_count, 0) AS test_case_count,
                tw.status,
                tw.created_at AS created_at,
                tw.last_accessed_at AS last_accessed_at
            FROM paged_workspaces tw
            LEFT JOIN sys_role r
                ON r.id = tw.workspace_role
               AND r.is_deleted = FALSE
               AND r.type = 'workspace'
            LEFT JOIN ws_project default_project
                ON default_project.id = tw.default_project_id
               AND default_project.workspace_id = tw.workspace_id
               AND default_project.is_deleted = FALSE
            LEFT JOIN member_stats ms ON ms.workspace_id = tw.workspace_id
            LEFT JOIN project_stats ps ON ps.workspace_id = tw.workspace_id
            LEFT JOIN case_stats cs ON cs.workspace_id = tw.workspace_id
            </script>
            """)
    List<WorkspaceMyRespDTO> selectPage(@Param("userId") UUID userId,
                                        @Param("keyword") String keyword,
                                        @Param("scope") String scope,
                                        @Param("adminRoleId") UUID adminRoleId,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ws_user wu
            JOIN ws_workspace w ON w.id = wu.workspace_id
            WHERE wu.user_id = #{userId}
              AND wu.is_deleted = FALSE
              AND w.is_deleted = FALSE
              AND w.status IN ('active', 'dissolved')
            <if test="keyword != null and keyword != ''">
              AND LOWER(w.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            </if>
            <choose>
                <when test="scope == 'managed'">
                    AND w.status = 'active'
                    AND wu.workspace_role = #{adminRoleId}
                </when>
                <when test="scope == 'archived'">
                    AND w.status = 'dissolved'
                </when>
            </choose>
            </script>
            """)
    long count(@Param("userId") UUID userId,
               @Param("keyword") String keyword,
               @Param("scope") String scope,
               @Param("adminRoleId") UUID adminRoleId);

    @Select("""
            <script>
            SELECT
                COUNT(*) AS all_count,
                COALESCE(SUM(CASE
                    WHEN w.status = 'active' AND wu.workspace_role = #{adminRoleId} THEN 1
                    ELSE 0
                END), 0) AS managed_count,
                COALESCE(SUM(CASE
                    WHEN w.status = 'dissolved' THEN 1
                    ELSE 0
                END), 0) AS archived_count
            FROM ws_user wu
            JOIN ws_workspace w ON w.id = wu.workspace_id
            WHERE wu.user_id = #{userId}
              AND wu.is_deleted = FALSE
              AND w.is_deleted = FALSE
              AND w.status IN ('active', 'dissolved')
            <if test="keyword != null and keyword != ''">
              AND LOWER(w.name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    @Results(id = "workspaceMyScopeCounts",
            value = {
                    @Result(column = "all_count", property = "all"),
                    @Result(column = "managed_count", property = "managed"),
                    @Result(column = "archived_count", property = "archived")
            })
    WorkspaceMyScopeCountsDTO countScopes(@Param("userId") UUID userId,
                                          @Param("keyword") String keyword,
                                          @Param("adminRoleId") UUID adminRoleId);
}
