package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;

/**
 * 缺陷状态机上下文：唯一裁决 status 迁移的域服务
 */
public interface BugWorkflow {

    /**
     * 裁决迁移并返回目标状态；非法跃迁抛业务异常（C3）
     */
    BugStatus transition(Bug bug, BugAction action);

    /**
     * 按迁移矩阵查询 from→to 是否合法，不抛错
     */
    boolean can(BugStatus from, BugStatus to);

    /**
     * 断言迁移合法，非法抛业务异常（C3）
     */
    void assertTransition(BugStatus from, BugStatus to);
}