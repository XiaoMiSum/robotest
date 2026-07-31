package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新需求池条目请求（PUT /api/project/requirements/{id}）：部分更新，字段为空表示不修改。
 */
@Data
public class RequirementUpdateReqDTO {

    @Size(max = 200, message = "条目标题不能超过 200 字符")
    private String title;

    private String content;

    /** 来源 URL：null 不修改，空串清空，非空更新 */
    private String sourceUrl;
}
