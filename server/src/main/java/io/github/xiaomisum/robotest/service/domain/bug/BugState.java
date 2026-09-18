package io.github.xiaomisum.robotest.service.domain.bug;

/**
 * 缺陷单状态的迁移决策：每个非次终态一个实现，允许表即该状态的迁移边
 */
public interface BugState {

    BugStatus code();

    /**
     * 动作是否产生合法迁移
     */
    boolean allows(BugAction action);

    /**
     * 裁决迁移并返回目标状态；非法动作抛 BUG_INVALID_STATUS_TRANSITION（C3）
     */
    BugStatus on(BugAction action);
}