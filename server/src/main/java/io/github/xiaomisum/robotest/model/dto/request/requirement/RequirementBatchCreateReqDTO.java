package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量创建需求池条目请求（POST /api/project/requirements/batch，US-AI-019）。
 *
 * <p>
 * content 长度上限为系统配置项 requirementContentMaxLength，在 Service 层统一校验；
 * aiGenerated 仅作展示标记（缺省 false，接受客户端透传），不影响业务规则（详细设计 3.1.7）。
 * </p>
 */
@Data
public class RequirementBatchCreateReqDTO {

    @NotEmpty(message = "条目列表不能为空")
    @Size(max = 100, message = "单次批量创建不得超过 100 条")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "条目标题不能为空")
        @Size(max = 200, message = "条目标题不能超过 200 字符")
        private String title;

        @NotBlank(message = "需求内容不能为空")
        private String content;

        /** 来源 URL，可空（仅记录出处，平台不抓取） */
        private String sourceUrl;

        /** AI 拆分入库标识，缺省 false（仅作展示标记） */
        private Boolean aiGenerated;
    }
}
