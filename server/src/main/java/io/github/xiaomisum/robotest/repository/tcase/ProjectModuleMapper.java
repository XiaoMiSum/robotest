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