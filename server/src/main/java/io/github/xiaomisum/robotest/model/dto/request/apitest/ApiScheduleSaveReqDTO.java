package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    /** UUID 以字符串传输，归属校验由 Service 完成 */
    @NotNull
    private UUID boundObjectId;

    /** task_type = scene_execute 时必填（目标环境） */
    private UUID environmentId;

    @NotBlank
    @Size(max = 50)
    private String cronExpression;

    private Boolean enabled;

}
