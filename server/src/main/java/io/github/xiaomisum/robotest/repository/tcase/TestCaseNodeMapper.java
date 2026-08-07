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

    default void deleteByDocumentId(UUID documentId) {
        delete(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }

    default long countCaseNodesByDocumentIds(List<String> documentIds) {
        return selectCount(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE));
    }

    /** 关键词候选检索（4.3）：标题 ILIKE 匹配、按排序取前 limit 条，供遗漏测试点分析使用 */
    default List<TestCaseNode> listCaseNodesByDocumentIdsAndKeyword(List<UUID> documentIds, String keyword, int limit) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE)
                .like(TestCaseNode::getTitle, keyword)
                .orderByAsc(TestCaseNode::getSortOrder)
                .last("LIMIT " + limit));
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

    /** 文档内 case 类型节点（向量重建/补偿扫描口径） */
    default List<TestCaseNode> listCaseNodesByDocumentId(UUID documentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE)
                .orderByAsc(TestCaseNode::getSortOrder));
    }

    /** 多文档下全部 case 类型节点（回归推荐模块命中口径） */
    default List<TestCaseNode> listCaseNodesByDocumentIds(Collection<UUID> documentIds) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .in(TestCaseNode::getDocumentId, documentIds)
                .eq(TestCaseNode::getType, Constants.NodeType.CASE)
                .orderByAsc(TestCaseNode::getSortOrder));
    }
}
