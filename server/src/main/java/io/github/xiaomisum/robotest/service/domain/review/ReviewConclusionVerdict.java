package io.github.xiaomisum.robotest.service.domain.review;

/**
 * 评审结论判定（06 §5.2）——由评审域按节点 lastMark 统计确定性得出，不经 LLM。
 */
public enum ReviewConclusionVerdict {

    PASS("PASS"),
    FAIL("FAIL"),
    INCONCLUSIVE("INCONCLUSIVE");

    private final String code;

    ReviewConclusionVerdict(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}