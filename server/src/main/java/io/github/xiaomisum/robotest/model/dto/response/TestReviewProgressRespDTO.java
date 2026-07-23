package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

/**
 * 测试评审进度统计响应 DTO
 */
@Data
public class TestReviewProgressRespDTO {

    /**
     * 关联用例总数
     */
    private long totalAssociated;

    /**
     * 通过数
     */
    private long passed;

    /**
     * 不通过数
     */
    private long failed;

    /**
     * 未评审数
     */
    private long pending;

    /**
     * 评审进度百分比（0-100）
     */
    private double progressPercent;
}
