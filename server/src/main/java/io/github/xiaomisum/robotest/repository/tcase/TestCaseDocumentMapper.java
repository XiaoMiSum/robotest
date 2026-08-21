package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestCaseDocumentMapper extends BaseMapperX<TestCaseDocument> {

    default List<TestCaseDocument> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TestCaseDocument>()
                .in(TestCaseDocument::getId, ids));
    }

    default List<TestCaseDocument> listByProjectId(UUID projectId) {
        return selectList(new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .orderByAsc(TestCaseDocument::getSortOrder));
    }

    default TestCaseDocument findByModuleIdAndName(UUID moduleId, String name) {
        return selectOne(new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getModuleId, moduleId)
                .eq(TestCaseDocument::getName, name));
    }

    default List<TestCaseDocument> listByModuleId(UUID moduleId) {
        return selectList(new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getModuleId, moduleId)
                .orderByAsc(TestCaseDocument::getSortOrder));
    }

    default List<TestCaseDocument> listByModuleIdNotEqual(UUID excludeId, UUID moduleId) {
        return selectList(new LambdaQueryWrapperX<TestCaseDocument>()
                .ne(TestCaseDocument::getId, excludeId)
                .eq(TestCaseDocument::getModuleId, moduleId));
    }

    default List<TestCaseDocument> listByModuleIdAndProjectId(UUID projectId, UUID moduleId) {
        return selectList(new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .eq(TestCaseDocument::getModuleId, moduleId));
    }

    default long countByModuleId(UUID moduleId) {
        return selectCount(new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getModuleId, moduleId));
    }

    default TestCaseDocument findByNameAndModule(UUID projectId, UUID moduleId, String name) {
        LambdaQueryWrapperX<TestCaseDocument> wrapper = new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .eq(TestCaseDocument::getName, name);
        if (moduleId != null) {
            wrapper.eq(TestCaseDocument::getModuleId, moduleId);
        } else {
            wrapper.isNull(TestCaseDocument::getModuleId);
        }
        return selectOne(wrapper);
    }

    default TestCaseDocument findByNameAndModuleExcludingId(UUID projectId, UUID moduleId, String name, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseDocument> wrapper = new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .eq(TestCaseDocument::getName, name)
                .ne(TestCaseDocument::getId, excludeId);
        if (moduleId != null) {
            wrapper.eq(TestCaseDocument::getModuleId, moduleId);
        } else {
            wrapper.isNull(TestCaseDocument::getModuleId);
        }
        return selectOne(wrapper);
    }

    default TestCaseDocument findByNameExcludingId(UUID projectId, UUID moduleId, String name, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseDocument> wrapper = new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .eq(TestCaseDocument::getName, name)
                .ne(TestCaseDocument::getId, excludeId);
        if (moduleId != null) {
            wrapper.eq(TestCaseDocument::getModuleId, moduleId);
        } else {
            wrapper.isNull(TestCaseDocument::getModuleId);
        }
        return selectOne(wrapper);
    }

    default List<TestCaseDocument> findSiblingsByModule(UUID projectId, UUID moduleId, UUID excludeId) {
        LambdaQueryWrapperX<TestCaseDocument> wrapper = new LambdaQueryWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getProjectId, projectId)
                .ne(TestCaseDocument::getId, excludeId)
                .orderByAsc(TestCaseDocument::getSortOrder);
        if (moduleId != null) {
            wrapper.eq(TestCaseDocument::getModuleId, moduleId);
        } else {
            wrapper.isNull(TestCaseDocument::getModuleId);
        }
        return selectList(wrapper);
    }

    default int updateSortOrder(UUID id, int sortOrder) {
        return update(null, new LambdaUpdateWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getId, id)
                .set(TestCaseDocument::getSortOrder, sortOrder));
    }

    default void updateName(UUID id, String name) {
        update(null, new LambdaUpdateWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getId, id)
                .set(TestCaseDocument::getName, name));
    }

    default void updateModuleAndOrder(UUID id, UUID moduleId, int sortOrder) {
        update(null, new LambdaUpdateWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getId, id)
                .set(TestCaseDocument::getModuleId, moduleId)
                .set(TestCaseDocument::getSortOrder, sortOrder));
    }

    default void updateLayout(UUID id, java.util.Map<String, Object> layout) {
        update(null, new LambdaUpdateWrapperX<TestCaseDocument>()
                .eq(TestCaseDocument::getId, id)
                .set(TestCaseDocument::getLayout, layout));
    }

    @SuppressWarnings("unchecked")
    default java.util.Map<String, Object> getLayout(UUID id) {
        TestCaseDocument doc = selectById(id);
        return doc != null ? (java.util.Map<String, Object>) doc.getLayout() : null;
    }
}
