package io.github.xiaomisum.robotest.model.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存智能体自定义模板请求
 */
@Data
public class AiAgentSaveReqDTO {

    @NotBlank(message = "角色指令段不能为空")
    @Size(max = 8000, message = "角色指令段长度不能超过8000个字符")
    private String roleInstruction;

    @NotNull(message = "格式约束段编辑开关不能为空")
    private Boolean formatEditable;

    @Size(max = 8000, message = "输出格式约束段长度不能超过8000个字符")
    private String formatConstraint;
}
