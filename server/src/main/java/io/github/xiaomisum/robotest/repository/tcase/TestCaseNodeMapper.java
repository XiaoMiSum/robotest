package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestCaseNodeMapper extends BaseMapperX<TestCaseNode> {

    default List<TestCaseNode> listByDocumentId(UUID documentId) {
        return selectList(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }

    default void deleteByDocumentId(UUID documentId) {
        delete(new LambdaQueryWrapperX<TestCaseNode>()
                .eq(TestCaseNode::getDocumentId, documentId));
    }
}
