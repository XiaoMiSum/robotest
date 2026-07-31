package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 智能体详情（当前生效段 + 内置默认段）
 */
@Data
public class AiAgentDetailRespDTO {

    private String functionType;
    private String name;
    private Boolean customized;
    private Boolean formatEditable;
    private String roleInstruction;
    private String formatConstraint;
    private Defaults defaults;

    @Data
    public static class Defaults {
        private String roleInstruction;
        private String formatConstraint;
    }
}
