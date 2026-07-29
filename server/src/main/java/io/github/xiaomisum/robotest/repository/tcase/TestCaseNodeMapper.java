package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestCaseNodeMapper extends BaseMapperX<TestCaseNode> {

    default List<TestCaseNode> listByDocumentId(UUID documentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }

    default List<TestCaseNode> listByParentId(UUID parentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getParentId, parentId));
    }

    default void deleteByNodeIds(Collection<UUID> ids) {
        delete(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getId, ids));
    }

    default int updateAttrsWithVersion(UUID nodeId, int currentVersion, String title, String type,
                                        String priority, Integer sortOrder) {
        var wrapper = new LambdaUpdateWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getId, nodeId)
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, currentVersion + 1);
        if (title != null) wrapper.set(TestCaseNode::getTitle, title);
        if (type != null) wrapper.set(TestCaseNode::getType, type);
        if (priority != null) wrapper.set(TestCaseNode::getPriority, priority);
        if (sortOrder != null) wrapper.set(TestCaseNode::getSortOrder, sortOrder);
        return update(null, wrapper);
    }

    default int moveNodeWithVersion(UUID nodeId, int currentVersion, UUID parentId, Integer sortOrder) {
        var wrapper = new LambdaUpdateWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getId, nodeId)
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, currentVersion + 1);
        if (parentId != null) wrapper.set(TestCaseNode::getParentId, parentId);
        if (sortOrder != null) wrapper.set(TestCaseNode::getSortOrder, sortOrder);
        return update(null, wrapper);
    }

    default void deleteByDocumentId(UUID documentId) {
        delete(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }

    default long countCaseNodesByDocumentIds(List<String> documentIds) {
        return selectCount(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE));
    }

    default PageResult<TestCaseNode> findCasePage(PageParam pageParam, List<String> documentIds, String keyword, String priority) {
        var wrapper = new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE)
                .likeIfPresent(TestCaseNode::getTitle, keyword)
                .eqIfPresent(TestCaseNode::getPriority, priority)
                .orderByAsc(TestCaseNode::getSortOrder);
        return selectPage(pageParam, wrapper);
    }
}
