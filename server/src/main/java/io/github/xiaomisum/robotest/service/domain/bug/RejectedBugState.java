package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * REJECTED 状态的迁移表（矩阵第三行）：CLOSE→CLOSED、REOPEN→ACTIVE（重新激活），RESOLVE/REJECT 为非法边
 */
public class RejectedBugState implements BugState {

    private static final Set<BugAction> ALLOWED = EnumSet.of(BugAction.CLOSE, BugAction.REOPEN);

    @Override
    public BugStatus code() {
        return BugStatus.REJECTED;
    }

    @Override
    public boolean allows(BugAction action) {
        return ALLOWED.contains(action);
    }

    @Override
    public BugStatus on(BugAction action) {
        if (!allows(action)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
        return action.target();
    }
}