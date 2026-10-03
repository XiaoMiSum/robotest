package io.github.xiaomisum.robotest.model.dto.request.requirement;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 更新需求请求（PUT /api/project/requirements/{id}，详设 3.5）：
 * 部分更新（C11），请求体只包含发生变化的字段，null 表示不修改；不接受 status（状态仅经 3.6/3.7 专用接口流转）。
 */
@Data
public class RequirementUpdateReqDTO {

    @Size(max = 300, message = "需求标题不能超过 300 字符")
    private String title;

    private String description;

    private UUID moduleId;

    private String systemVersion;

    private String priority;

    private UUID ownerId;

    private List<String> tags;
}
