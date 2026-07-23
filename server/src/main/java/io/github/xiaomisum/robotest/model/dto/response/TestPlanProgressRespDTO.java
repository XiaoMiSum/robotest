package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

/**
 * 测试计划执行进度统计响应 DTO
 */
@Data
public class TestPlanProgressRespDTO {

    /**
     * 关联用例总数
     */
    private long totalAssociated;

    /**
     * 通过数
     */
    private long passed;

    /**
     * 失败数
     */
    private long failed;

    /**
     * 阻塞数
     */
    private long blocked;

    /**
     * 未执行数
     */
    private long untested;

    /**
     * 执行进度百分比（0-100）
     */
    private double progressPercent;
}
