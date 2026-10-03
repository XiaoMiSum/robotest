package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * 需求编号分配（详设 4.1）：创建与采纳落库共用的 REQ- 序号分配器。
 */
@Component
public class RequirementCodeAllocator {

    /** 唯一冲突重试上限：超过即放弃，抛 1000018002 由调用方决定回滚口径 */
    private static final int CODE_ALLOCATE_MAX_RETRIES = 3;

    @Resource
    private RequirementMapper requirementMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    /**
     * 分配编号并插入（详设 4.1）：项目内 max+1 起零填充，依赖 uk_requirement_project_code 防并发，
     * 唯一冲突时重取序号重试（最多 3 次）。每次尝试落在嵌套事务（SAVEPOINT）上，
     * 避免 PG 唯一冲突污染外层事务状态导致重试必然失败。
     */
    public void insertWithCodeAllocation(UUID projectId, Requirement item) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        int retries = 0;
        while (true) {
            item.setCode(String.format("REQ-%03d", requirementMapper.selectMaxSeq(projectId) + 1));
            try {
                nested.executeWithoutResult(status -> requirementMapper.insert(item));
                return;
            } catch (DuplicateKeyException e) {
                if (++retries > CODE_ALLOCATE_MAX_RETRIES) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_CODE_CONFLICT);
                }
            }
        }
    }
}
