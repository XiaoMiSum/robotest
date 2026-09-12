package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 创建/更新定时任务（定时任务详细设计 3.1.2/3.1.3）
 */
@Data
public class ApiScheduleSaveReqDTO {

    @NotBlank
    @Pattern(regexp = "scene_execute|import_swagger", message = "任务类型仅支持 scene_execute / import_swagger")
    private String taskType;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 500)
    private String description;

    /** 执行方式（scene_execute 任务）：all / modules / scenes，是否必填由 Service 按类型校验 */
    private String executionScope;

    /** 指定模块（多选），execution_scope=modules 时 Service 校验必填 */
    private List<UUID> moduleIds;

    /** 指定场景（多选），execution_scope=scenes 时 Service 校验必填 */
    private List<UUID> sceneIds;

    /** import_swagger 任务必填（OpenAPI/Swagger JSON 文件 URL） */
    @Size(max = 2000)
    private String openapiUrl;

    /** task_type = scene_execute 时必填（目标环境） */
    private UUID environmentId;

    @NotBlank
    @Size(max = 50)
    private String cronExpression;

    private Boolean enabled;

}