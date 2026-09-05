package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 场景级草稿执行响应（创建态未保存场景一键执行，测试场景详细设计 3.6.4） */
@Data
@Builder
public class ApiSceneDraftExecuteRespDTO {

    /** success 表示全部通过；failed 表示部分失败；error 表示引擎级异常 */
    private String status;

    private int passed;

    private int failed;

    private int skipped;

    private Long durationMs;

    private List<StepResult> steps;

    @Data
    @Builder
    public static class StepResult {

        /** success / failed / skipped / error */
        private String status;

        private String name;

        private Integer durationMs;

        private Map<String, Object> request;

        private Map<String, Object> response;

        private String errorMessage;

    }

}