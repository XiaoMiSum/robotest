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

    default List<TestCaseNode> listByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getId, ids));
    }

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
                                        String priority, Integer sortOrder, Boolean aiGenerated) {
        var wrapper = new LambdaUpdateWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getId, nodeId)
                .eq(TestCaseNode::getVersion, currentVersion)
                .set(TestCaseNode::getVersion, currentVersion + 1);
        if (title != null) wrapper.set(TestCaseNode::getTitle, title);
        if (type != null) wrapper.set(TestCaseNode::getType, type);
        if (priority != null) wrapper.set(TestCaseNode::getPriority, priority);
        if (sortOrder != null) wrapper.set(TestCaseNode::getSortOrder, sortOrder);
        if (aiGenerated != null) wrapper.set(TestCaseNode::getAiGenerated, aiGenerated);
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

    /** AI 助手属性子节点重排：只写序号，内容与版本不动（C11 部分更新） */
    default int updateSortOrder(UUID id, int sortOrder) {
        return update(null, new LambdaUpdateWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getId, id)
                .set(TestCaseNode::getSortOrder, sortOrder));
    }

    default void deleteByDocumentId(UUID documentId) {
        delete(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }

    default long countCaseNodesByDocumentIds(Collection<UUID> documentIds) {
        return selectCount(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE));
    }

    /** 按文档批量取用例节点（圈选范围装配）；空集合不发起查询 */
    default List<TestCaseNode> listCasesByDocumentIds(Collection<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE));
    }

    default PageResult<TestCaseNode> findCasePage(PageParam pageParam, Collection<UUID> documentIds, String keyword, String priority) {
        var wrapper = new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE)
                .likeIfPresent(TestCaseNode::getTitle, keyword)
                .eqIfPresent(TestCaseNode::getPriority, priority)
                .orderByAsc(TestCaseNode::getSortOrder);
        return selectPage(pageParam, wrapper);
    }
}
