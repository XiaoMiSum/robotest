package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocumentLayout;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface TestCaseDocumentLayoutMapper extends BaseMapperX<TestCaseDocumentLayout> {

    default TestCaseDocumentLayout findByDocumentId(UUID documentId) {
        return selectOne(new LambdaQueryWrapperX<TestCaseDocumentLayout>()
                .eq(TestCaseDocumentLayout::getDocumentId, documentId));
    }
}
