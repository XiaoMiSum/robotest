package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建需求池条目请求（POST /api/project/requirements）。
 */
@Data
public class RequirementCreateReqDTO {

    @NotBlank(message = "条目标题不能为空")
    @Size(max = 200, message = "条目标题不能超过 200 字符")
    private String title;

    @NotBlank(message = "需求内容不能为空")
    private String content;

    /** 来源 URL，可空（仅记录出处，平台不抓取） */
    private String sourceUrl;
}
