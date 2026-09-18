package io.github.xiaomisum.robotest.service.domain.bug;

/**
 * 缺陷变更操作类型：消费端据此区分"写向量/删向量"分支（与 05 §3.1.2 一致）
 */
public enum BugChangeOp {
    CREATED, UPDATED, CLOSED
}