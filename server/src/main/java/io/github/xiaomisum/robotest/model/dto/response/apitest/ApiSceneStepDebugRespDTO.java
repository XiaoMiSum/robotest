package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 单步骤调试响应（测试场景详细设计 3.6.3） */
@Data
@Builder
public class ApiSceneStepDebugRespDTO {

    private StepResult stepResult;

    @Data
    @Builder
    public static class StepResult {

        private String stepId;

        /** success / failed / error */
        private String status;

        private Integer durationMs;

        private Map<String, Object> request;

        private Map<String, Object> response;

        /** [{name, passed, message}] */
        private List<Map<String, Object>> validatorResults;

        /** 提取器产出 {变量名: 值} */
        private Map<String, Object> extractedVariables;

    }

}
