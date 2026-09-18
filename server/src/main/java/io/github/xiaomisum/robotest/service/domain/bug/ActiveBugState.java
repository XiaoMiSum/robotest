package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * ACTIVE 状态的迁移表（矩阵首行）：仅 RESOLVE→RESOLVED、REJECT→REJECTED，CLOSE/REOPEN 为非法边
 */
public class ActiveBugState implements BugState {

    private static final Set<BugAction> ALLOWED = EnumSet.of(BugAction.RESOLVE, BugAction.REJECT);

    @Override
    public BugStatus code() {
        return BugStatus.ACTIVE;
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