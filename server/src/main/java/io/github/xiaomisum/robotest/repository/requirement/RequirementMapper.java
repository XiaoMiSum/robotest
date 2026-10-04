package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RequirementMapper extends BaseMapperX<Requirement> {

    /**
     * 需求列表（详设 3.2）：隔离 + 多维筛选 + 关键词三段匹配（4.3：编号前缀 / 编号后缀 / 标题包含）。
     * coverage 筛选联查 trace_coverage_result：pending 即「无结论记录」，其余按结论状态匹配；
     * 过滤恒按库内结论执行，不随 AI 总开关变化（开关只影响展示值，详设 3.2 校验规则）。
     */
    default PageResult<Requirement> findPage(PageParam pageParam, UUID projectId, List<String> status,
            List<UUID> moduleIds, UUID ownerId, String systemVersion, String keyword, String coverage) {
        LambdaQueryWrapperX<Requirement> wrapper = new LambdaQueryWrapperX<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eqIfPresent(Requirement::getOwnerId, ownerId)
                .eqIfPresent(Requirement::getSystemVersion, systemVersion);
        if (status != null && !status.isEmpty()) {
            wrapper.in(Requirement::getStatus, status);
        }
        if (moduleIds != null && !moduleIds.isEmpty()) {
            wrapper.in(Requirement::getModuleId, moduleIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.likeRight(Requirement::getCode, kw)
                    .or().like(Requirement::getCode, kw)
                    .or().apply("requirement.title ILIKE {0}", "%" + kw + "%"));
        }
        if (coverage != null && !coverage.isBlank()) {
            String value = coverage.trim();
            if ("pending".equals(value)) {
                wrapper.apply("NOT EXISTS (SELECT 1 FROM trace_coverage_result c "
                        + "WHERE c.project_id = {0} AND c.requirement_id = requirement.id AND c.is_deleted = FALSE)",
                        projectId);
            } else {
                wrapper.apply("EXISTS (SELECT 1 FROM trace_coverage_result c "
                        + "WHERE c.project_id = {0} AND c.requirement_id = requirement.id AND c.is_deleted = FALSE "
                        + "AND c.coverage_status = {1})", projectId, value);
            }
        }
        wrapper.orderByDesc(Requirement::getUpdatedAt);
        return selectPage(pageParam, wrapper);
    }

    /** 按 ID 批量读取（追溯节点解析，详设 3.4） */
    default List<Requirement> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<Requirement>().in(Requirement::getId, ids));
    }

    /**
     * 项目内既有编号的最大序号（详设 4.1）：只统计项目内非删除的合规编号，
     * 归档条目占用的编号不回收，保证追溯链编号稳定可引用。
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM 5) AS INTEGER)), 0) FROM requirement "
            + "WHERE project_id = #{projectId} AND code ~ '^REQ-[0-9]+$' AND is_deleted = FALSE")
    int selectMaxSeq(@Param("projectId") UUID projectId);
}
