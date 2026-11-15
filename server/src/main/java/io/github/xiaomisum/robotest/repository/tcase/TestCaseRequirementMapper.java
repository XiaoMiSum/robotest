package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseRequirement;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TestCaseRequirementMapper extends BaseMapperX<TestCaseRequirement> {

    default List<TestCaseRequirement> listByCaseNodeId(UUID projectId, UUID caseNodeId) {
        return selectList(new LambdaQueryWrapperX<TestCaseRequirement>()
                .eq(TestCaseRequirement::getProjectId, projectId)
                .eq(TestCaseRequirement::getCaseNodeId, caseNodeId));
    }

    /** 计划/评审创建：按需求批量反查关联用例，空集合不发起查询 */
    default List<TestCaseRequirement> listByRequirementIds(UUID projectId, Collection<UUID> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<TestCaseRequirement>()
                .eq(TestCaseRequirement::getProjectId, projectId)
                .in(TestCaseRequirement::getRequirementId, requirementIds));
    }

    default void deleteByCaseNodeId(UUID projectId, UUID caseNodeId) {
        delete(new LambdaQueryWrapperX<TestCaseRequirement>()
                .eq(TestCaseRequirement::getProjectId, projectId)
                .eq(TestCaseRequirement::getCaseNodeId, caseNodeId));
    }
}
