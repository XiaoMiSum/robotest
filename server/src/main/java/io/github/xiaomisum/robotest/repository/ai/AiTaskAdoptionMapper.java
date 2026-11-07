package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiTaskAdoption;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface AiTaskAdoptionMapper extends BaseMapperX<AiTaskAdoption> {

    /** 幂等判定（uk_ai_task_adoption 兜底前的查询校验，总册 3.6.5：键按执行命名空间） */
    default AiTaskAdoption selectByExecutionAndKey(UUID executionId, String proposalKey) {
        return selectOne(new LambdaQueryWrapperX<AiTaskAdoption>()
                .eq(AiTaskAdoption::getExecutionId, executionId)
                .eq(AiTaskAdoption::getProposalKey, proposalKey)
                .last("LIMIT 1"));
    }

    /** 指定执行的采纳记录（详情清单回填 confirmStatus 动作） */
    default List<AiTaskAdoption> selectByExecution(UUID executionId) {
        return selectList(new LambdaQueryWrapperX<AiTaskAdoption>()
                .eq(AiTaskAdoption::getExecutionId, executionId)
                .orderByAsc(AiTaskAdoption::getCreatedAt));
    }

    /** 指定执行的采纳记录数（与待采纳提案数比对，判定是否全部处置完毕） */
    default long countByExecution(UUID executionId) {
        return selectCount(new LambdaQueryWrapperX<AiTaskAdoption>()
                .eq(AiTaskAdoption::getExecutionId, executionId));
    }
}
