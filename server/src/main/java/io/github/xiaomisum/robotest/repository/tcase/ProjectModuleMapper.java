package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectModuleMapper extends BaseMapperX<ProjectModule> {

    default List<ProjectModule> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .in(ProjectModule::getId, ids));
    }

    default List<ProjectModule> listByProjectId(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .orderByAsc(ProjectModule::getSortOrder));
    }

    default ProjectModule findByParentIdAndName(UUID parentId, String name) {
        return selectOne(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getParentId, parentId)
                .eq(ProjectModule::getName, name));
    }

    default List<ProjectModule> listByParentId(UUID parentId) {
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getParentId, parentId)
                .orderByAsc(ProjectModule::getSortOrder));
    }

    /** 按源侧稳定键批量认领既有模块（包容器 source_id=包名、类模块 source_id=全限定名，BR-1.8） */
    default List<ProjectModule> listByProjectAndSourceIds(UUID projectId, Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .in(ProjectModule::getSourceId, sourceIds));
    }

    /** 根级模块按源侧键批量认领（包容器恒在根级：parent_id 为空 + source_id = 完整包名，BR-1.8） */
    default List<ProjectModule> listRootBySourceIds(UUID projectId, Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .isNull(ProjectModule::getParentId)
                .in(ProjectModule::getSourceId, sourceIds));
    }

    /** 导入归属纠偏：源侧稳定键命中但父容器已变化时改挂到当前包容器（GitLab 集成详设 3.5） */
    default void reparent(UUID id, UUID parentId) {
        update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getParentId, parentId));
    }

    /** 「同包容器 + 同简单名」认领后回填全限定名（BR-1.8：回填后复用，不新建重复节点） */
    default void backfillSourceId(UUID id, String sourceId) {
        update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getSourceId, sourceId));
    }

    default List<ProjectModule> listByParentIdNotEqual(UUID excludeId, UUID parentId) {
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .ne(ProjectModule::getId, excludeId)
                .eq(ProjectModule::getParentId, parentId));
    }

    default List<ProjectModule> listByParentIdAndProjectId(UUID projectId, UUID parentId) {
        return selectList(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .eq(ProjectModule::getParentId, parentId));
    }

    default long countByParentId(UUID parentId) {
        return selectCount(new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getParentId, parentId));
    }

    default ProjectModule findByNameAndParent(UUID projectId, UUID parentId, String name) {
        LambdaQueryWrapperX<ProjectModule> wrapper = new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .eq(ProjectModule::getName, name);
        if (parentId != null) {
            wrapper.eq(ProjectModule::getParentId, parentId);
        } else {
            wrapper.isNull(ProjectModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default ProjectModule findByNameAndParentExcludingId(UUID projectId, UUID parentId, String name, UUID excludeId) {
        LambdaQueryWrapperX<ProjectModule> wrapper = new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .eq(ProjectModule::getName, name)
                .ne(ProjectModule::getId, excludeId);
        if (parentId != null) {
            wrapper.eq(ProjectModule::getParentId, parentId);
        } else {
            wrapper.isNull(ProjectModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default ProjectModule findByNameExcludingId(UUID projectId, UUID parentId, String name, UUID excludeId) {
        LambdaQueryWrapperX<ProjectModule> wrapper = new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .eq(ProjectModule::getName, name)
                .ne(ProjectModule::getId, excludeId);
        if (parentId != null) {
            wrapper.eq(ProjectModule::getParentId, parentId);
        } else {
            wrapper.isNull(ProjectModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default List<ProjectModule> findSiblingsByParent(UUID projectId, UUID parentId, UUID excludeId) {
        LambdaQueryWrapperX<ProjectModule> wrapper = new LambdaQueryWrapperX<ProjectModule>()
                .eq(ProjectModule::getProjectId, projectId)
                .ne(ProjectModule::getId, excludeId)
                .orderByAsc(ProjectModule::getSortOrder);
        if (parentId != null) {
            wrapper.eq(ProjectModule::getParentId, parentId);
        } else {
            wrapper.isNull(ProjectModule::getParentId);
        }
        return selectList(wrapper);
    }

    default int updateSortOrder(UUID id, int sortOrder) {
        return update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getSortOrder, sortOrder));
    }

    default void updateModule(UUID id, UUID parentId, int sortOrder) {
        update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getParentId, parentId)
                .set(ProjectModule::getSortOrder, sortOrder));
    }

    default void updateName(UUID id, String name) {
        update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getName, name));
    }

    default void updateParentAndOrder(UUID id, UUID parentId, int sortOrder) {
        update(null, new LambdaUpdateWrapperX<ProjectModule>()
                .eq(ProjectModule::getId, id)
                .set(ProjectModule::getParentId, parentId)
                .set(ProjectModule::getSortOrder, sortOrder));
    }
}