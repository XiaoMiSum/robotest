package io.github.xiaomisum.robotest.service.domain.review;

/**
 * 评审状态枚举，code 与 Constants.Status 保持一致（落库小写，C5 免迁移）
 */
public enum ReviewStatus {

    NEW("new"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    private final String code;

    ReviewStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 按落库值的反查：非法或空值返回 null 交由调用方兜底/抛错，避免枚举静默吞未知状态
     */
    public static ReviewStatus fromCode(String code) {
        for (ReviewStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}