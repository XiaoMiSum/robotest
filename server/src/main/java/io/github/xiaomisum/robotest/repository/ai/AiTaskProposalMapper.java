package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.List;
import java.util.UUID;

public interface AiTaskProposalMapper extends BaseMapperX<AiTaskProposal> {

    /** 指定执行的提案清单（详情摘要 / 确认定位，按产出顺序） */
    default List<AiTaskProposal> selectByExecution(UUID executionId) {
        return selectList(new LambdaQueryWrapperX<AiTaskProposal>()
                .eq(AiTaskProposal::getExecutionId, executionId)
                .orderByAsc(AiTaskProposal::getCreatedAt));
    }

    /** 指定执行内按键定位提案（3.6.5 确认与提案详情，键按执行命名空间） */
    default AiTaskProposal selectByExecutionAndKey(UUID executionId, String proposalKey) {
        return selectOne(new LambdaQueryWrapperX<AiTaskProposal>()
                .eq(AiTaskProposal::getExecutionId, executionId)
                .eq(AiTaskProposal::getProposalKey, proposalKey)
                .last("LIMIT 1"));
    }

    /** 待采纳提案数（confirm_status = pending；全部处置完毕后任务转 closed 的判定依据） */
    default long countPendingByExecution(UUID executionId) {
        return selectCount(new LambdaQueryWrapperX<AiTaskProposal>()
                .eq(AiTaskProposal::getExecutionId, executionId)
                .eq(AiTaskProposal::getConfirmStatus, Constants.AiProposalConfirmStatus.PENDING));
    }
}
