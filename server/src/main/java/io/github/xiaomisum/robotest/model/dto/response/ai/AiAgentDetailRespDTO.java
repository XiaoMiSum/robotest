package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

/**
 * 智能体详情（当前生效段，内容全部来自数据库）
 */
@Data
public class AiAgentDetailRespDTO {

    private String functionType;
    private String name;
    private Boolean customized;
    private Boolean formatEditable;
    private String roleInstruction;
    private String formatConstraint;
}
