package io.github.xiaomisum.robotest.service.domain.review;

/**
 * 评审状态机事件（对应迁移矩阵 §3.1.1 列）
 */
public enum ReviewEvent {

    /**
     * 提交评审记录（意见/标记），NEW 下首次 mark 触发自动升级 IN_PROGRESS
     */
    SUBMIT_RECORD,

    /**
     * 调整规划用例
     */
    UPDATE_CASES,

    /**
     * 同步快照
     */
    SYNC,

    /**
     * 完成评审
     */
    COMPLETE,

    /**
     * 软删评审（无状态变化，仅合法性许可）
     */
    DELETE
}