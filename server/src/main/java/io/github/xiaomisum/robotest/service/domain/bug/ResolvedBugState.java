package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * RESOLVED 状态的迁移表（矩阵第二行）：CLOSE→CLOSED（默认闭环）、REOPEN→ACTIVE（重开），RESOLVE/REJECT 为非法边
 */
public class ResolvedBugState implements BugState {

    private static final Set<BugAction> ALLOWED = EnumSet.of(BugAction.CLOSE, BugAction.REOPEN);

    @Override
    public BugStatus code() {
        return BugStatus.RESOLVED;
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