package io.github.xiaomisum.robotest.repository.tcase;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseRequirement;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

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

    /** 按用例节点更新关联需求；返回影响行数，0 表示当前无可更新关联 */
    default int updateRequirementByCaseNode(UUID projectId, UUID caseNodeId, UUID requirementId) {
        LambdaUpdateWrapperX<TestCaseRequirement> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(TestCaseRequirement::getProjectId, projectId)
                .eq(TestCaseRequirement::getCaseNodeId, caseNodeId)
                .eq(TestCaseRequirement::getIsDeleted, false)
                .set(TestCaseRequirement::getRequirementId, requirementId);
        return update(null, wrapper);
    }

    default void deleteByCaseNodeId(UUID projectId, UUID caseNodeId) {
        delete(new LambdaQueryWrapperX<TestCaseRequirement>()
                .eq(TestCaseRequirement::getProjectId, projectId)
                .eq(TestCaseRequirement::getCaseNodeId, caseNodeId));
    }
}
