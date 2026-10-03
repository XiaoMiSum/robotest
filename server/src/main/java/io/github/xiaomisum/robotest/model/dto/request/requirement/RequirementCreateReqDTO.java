package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 创建需求请求（POST /api/project/requirements，详设 3.3）。
 * systemVersion / priority 的业务校验在服务端执行以返回专有错误码（1000018010），
 * 故不使用 Bean Validation 注解提前拦截。
 */
@Data
public class RequirementCreateReqDTO {

    @NotBlank(message = "需求标题不能为空")
    @Size(max = 300, message = "需求标题不能超过 300 字符")
    private String title;

    /** 需求描述正文（Markdown），可空 */
    private String description;

    /** 归属模块，非空时须属当前项目（否则 1000018005） */
    private UUID moduleId;

    /** 被测业务系统版本，≤50 字符（超出 1000018010） */
    private String systemVersion;

    /** 优先级：high / medium / low（否则 1000018010） */
    private String priority;

    /** 负责人，须为当前工作空间成员（否则 1000018010） */
    private UUID ownerId;

    private List<String> tags;
}
