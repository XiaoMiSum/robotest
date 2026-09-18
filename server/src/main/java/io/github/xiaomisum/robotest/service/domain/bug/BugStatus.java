package io.github.xiaomisum.robotest.service.domain.bug;

/**
 * 缺陷状态枚举，code 与 Constants.BugStatus 保持一致（落库小写，C5 免迁移）
 */
public enum BugStatus {

    ACTIVE("active"),
    RESOLVED("resolved"),
    REJECTED("rejected"),
    CLOSED("closed");

    private final String code;

    BugStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 按落库值反查：非法或空值返回 null，交由调用方按"非法流转"拒绝，
     * 避免枚举静默吞掉脏数据状态
     */
    public static BugStatus fromCode(String code) {
        for (BugStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}