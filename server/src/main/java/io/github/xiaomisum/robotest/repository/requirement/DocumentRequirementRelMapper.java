package io.github.xiaomisum.robotest.repository.requirement;

import io.github.xiaomisum.robotest.model.entity.requirement.DocumentRequirementRel;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentRequirementRelMapper extends BaseMapperX<DocumentRequirementRel> {

    default List<DocumentRequirementRel> listByDocumentId(UUID documentId) {
        return selectList(new LambdaQueryWrapperX<DocumentRequirementRel>()
                .eq(DocumentRequirementRel::getDocumentId, documentId));
    }

    /** 逻辑删除文档下指定条目的关联（差量删除多余关联时使用） */
    default void deleteByDocumentIdAndRequirementIds(UUID documentId, Collection<UUID> requirementIds) {
        if (requirementIds.isEmpty()) {
            return;
        }
        delete(new LambdaQueryWrapperX<DocumentRequirementRel>()
                .eq(DocumentRequirementRel::getDocumentId, documentId)
                .in(DocumentRequirementRel::getRequirementId, requirementIds));
    }
}
