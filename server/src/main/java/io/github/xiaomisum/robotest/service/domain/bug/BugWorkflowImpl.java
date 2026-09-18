package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.Map;

/**
 * 缺陷状态机实现：按迁移矩阵（05 §3.1.1）逐格裁决。
 * CLOSED 为次终态（仅 REOPEN→ACTIVE 一条合法边），无状态类，直接在 workflow 处理，
 * 与评审状态机对 COMPLETED 的处理方式一致；未知状态按"空表"拒绝（对齐重构前
 * isValidTransition 的 getOrDefault 语义，脏数据不可继续流动）。
 */
@Service
public class BugWorkflowImpl implements BugWorkflow {

    private final Map<BugStatus, BugState> states;

    public BugWorkflowImpl() {
        this.states = Map.of(
                BugStatus.ACTIVE, new ActiveBugState(),
                BugStatus.RESOLVED, new ResolvedBugState(),
                BugStatus.REJECTED, new RejectedBugState());
    }

    @Override
    public BugStatus transition(Bug bug, BugAction action) {
        BugStatus from = BugStatus.fromCode(bug.getStatus());
        if (from == BugStatus.CLOSED) {
            if (action == BugAction.REOPEN) {
                return BugStatus.ACTIVE;
            }
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
        BugState state = from == null ? null : states.get(from);
        if (state == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
        return state.on(action);
    }

    @Override
    public boolean can(BugStatus from, BugStatus to) {
        BugAction action = BugAction.of(to);
        if (from == null || action == null) {
            return false;
        }
        if (from == BugStatus.CLOSED) {
            return action == BugAction.REOPEN;
        }
        BugState state = states.get(from);
        return state != null && state.allows(action);
    }

    @Override
    public void assertTransition(BugStatus from, BugStatus to) {
        if (!can(from, to)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_INVALID_STATUS_TRANSITION);
        }
    }
}