package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.List;
import java.util.UUID;

public interface TestCaseModuleMapper extends BaseMapperX<TestCaseModule> {

    default List<TestCaseModule> listByProjectId(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .orderByAsc(TestCaseModule::getSortOrder));
    }

    default TestCaseModule findByParentIdAndName(UUID parentId, String name) {
        return selectOne(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getParentId, parentId)
                .eq(TestCaseModule::getName, name));
    }

    default List<TestCaseModule> listByParentId(UUID parentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getParentId, parentId)
                .orderByAsc(TestCaseModule::getSortOrder));
    }

    default List<TestCaseModule> listByParentIdNotEqual(UUID excludeId, UUID parentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseModule>()
                .ne(TestCaseModule::getId, excludeId)
                .eq(TestCaseModule::getParentId, parentId));
    }

    default List<TestCaseModule> listByParentIdAndProjectId(UUID projectId, UUID parentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .eq(TestCaseModule::getParentId, parentId));
    }

    default long countByParentId(UUID parentId) {
        return selectCount(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getParentId, parentId));
    }

    default TestCaseModule findByNameAndParent(UUID projectId, UUID parentId, String name) {
        LambdaQueryWrapperX<TestCaseModule> wrapper = new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .eq(TestCaseModule::getName, name);
        if (parentId != null) {
            wrapper.eq(TestCaseModule::getParentId, parentId);
        } else {
            wrapper.isNull(TestCaseModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default TestCaseModule findByNameAndParentExcludingId(UUID projectId, UUID parentId, String name, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseModule> wrapper = new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .eq(TestCaseModule::getName, name)
                .ne(TestCaseModule::getId, excludeId);
        if (parentId != null) {
            wrapper.eq(TestCaseModule::getParentId, parentId);
        } else {
            wrapper.isNull(TestCaseModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default TestCaseModule findByNameExcludingId(UUID projectId, UUID parentId, String name, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseModule> wrapper = new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .eq(TestCaseModule::getName, name)
                .ne(TestCaseModule::getId, excludeId);
        if (parentId != null) {
            wrapper.eq(TestCaseModule::getParentId, parentId);
        } else {
            wrapper.isNull(TestCaseModule::getParentId);
        }
        return selectOne(wrapper);
    }

    default List<TestCaseModule> findSiblingsByParent(UUID projectId, UUID parentId, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseModule> wrapper = new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .ne(TestCaseModule::getId, excludeId)
                .orderByAsc(TestCaseModule::getSortOrder);
        if (parentId != null) {
            wrapper.eq(TestCaseModule::getParentId, parentId);
        } else {
            wrapper.isNull(TestCaseModule::getParentId);
        }
        return selectList(wrapper);
    }

    default int updateSortOrder(UUID id, int sortOrder) {
        return update(null, new LambdaUpdateWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getId, id)
                .set(TestCaseModule::getSortOrder, sortOrder));
    }

    default void updateModule(UUID id, UUID parentId, int sortOrder) {
        update(null, new LambdaUpdateWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getId, id)
                .set(TestCaseModule::getParentId, parentId)
                .set(TestCaseModule::getSortOrder, sortOrder));
    }

    default void updateName(UUID id, String name) {
        update(null, new LambdaUpdateWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getId, id)
                .set(TestCaseModule::getName, name));
    }

    default void updateParentAndOrder(UUID id, UUID parentId, int sortOrder) {
        update(null, new LambdaUpdateWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getId, id)
                .set(TestCaseModule::getParentId, parentId)
                .set(TestCaseModule::getSortOrder, sortOrder));
    }

    default List<TestCaseModule> findDocumentModulesByProjectId(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<TestCaseModule>()
                .eq(TestCaseModule::getProjectId, projectId)
                .eq(TestCaseModule::getType, Constants.ModuleType.DOCUMENT));
    }
}
