package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 函数试算结果（POST /api/project/functions/evaluate） */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiFunctionEvaluateRespDTO {

    /** 求值结果（字符串化） */
    private String result;

    /** 求值耗时（毫秒） */
    private Long durationMs;
}
