package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 生成用例树的结构化输出载体（详细设计 2.2）：
 * 用例子树生成、步骤补全、外部文本导入共用。
 *
 * <p>
 * title 长度不加 Bean Validation 约束——超长在结构断言前的宽容规整步骤中截断并计入
 * warnings（见
 * {@link io.github.xiaomisum.robotest.service.ai.support.AiNodeTreeAsserts}），
 * 不触发校验失败与带错重试。
 * </p>
 */
@Data
public class AiNodeTreeDTO {

    @NotBlank(message = "节点类型不能为空")
    private String type;

    @NotBlank(message = "节点标题不能为空")
    private String title;

    /** 仅允许出现在 case 节点，P0-P3 */
    private String priority;

    @Valid
    private List<AiNodeTreeDTO> children;

    /** done 帧载荷：{ nodes, warnings } */
    @Data
    public static class Payload {

        @Valid
        private List<AiNodeTreeDTO> nodes;
    }
}
