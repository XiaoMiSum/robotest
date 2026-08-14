package io.github.xiaomisum.robotest.model.dto.response.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 需求文档拆分的结构化输出载体（US-AI-019，详细设计 3.2.3）：
 * 模块分组 + 需求点条目，随 SSE done 帧返回（纯预览，不落库）。
 *
 * <p>
 * module/title 长度不加 Bean Validation 约束——超长在结构断言前的宽容规整步骤中
 * 截断并计入 warnings（见
 * {@link io.github.xiaomisum.robotest.service.ai.support.AiRequirementSplitAsserts}），
 * 不触发校验失败与带错重试。
 * </p>
 */
@Data
public class AiRequirementSplitRespDTO {

    @Valid
    private List<Module> modules;

    /** done 帧载荷：{ modules, warnings } */
    @Data
    public static class Payload {

        @Valid
        private List<Module> modules;
    }

    /** 模块分组：模块名 + 归属需求点条目 */
    @Data
    public static class Module {

        @NotBlank(message = "模块名不能为空")
        private String module;

        @Valid
        private List<Item> items;
    }

    /** 单个需求点：标题 + 内容（Markdown） */
    @Data
    public static class Item {

        @NotBlank(message = "需求点标题不能为空")
        private String title;

        @NotBlank(message = "需求点内容不能为空")
        private String content;
    }
}
