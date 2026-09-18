package io.github.xiaomisum.robotest.service.domain.bug;

/**
 * 缺陷状态机动作（对应 05 §3.1.1 迁移矩阵列）。
 * 动作与目标状态一一对应：REOPEN 是 ACTIVE 的唯一动作，can/assertTransition 按目标状态反查动作。
 */
public enum BugAction {

    RESOLVE(BugStatus.RESOLVED),
    REJECT(BugStatus.REJECTED),
    CLOSE(BugStatus.CLOSED),
    REOPEN(BugStatus.ACTIVE);

    private final BugStatus target;

    BugAction(BugStatus target) {
        this.target = target;
    }

    /**
     * 动作的目标状态：状态类只在"允许表"中记录动作，去向统一由动作携带，避免两处口径各写一份
     */
    public BugStatus target() {
        return target;
    }

    /**
     * 按目标状态反查动作；目标状态无唯一动作时返回 null（状态机上不存在该"到点"）
     */
    public static BugAction of(BugStatus target) {
        for (BugAction a : values()) {
            if (a.target == target) {
                return a;
            }
        }
        return null;
    }
}