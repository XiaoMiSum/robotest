package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 调试记录保存为接口定义（快速调试详细设计 3.1.3）
 */
@Data
public class ApiDebugSaveAsInterfaceReqDTO {

    /** create=新建接口；attach=归属已有接口（覆盖其主请求定义） */
    @NotBlank(message = "保存方式不能为空")
    @Pattern(regexp = "create|attach", message = "保存方式仅支持 create/attach")
    private String mode;

    /** mode=create 必填，所属模块内唯一 */
    @Size(max = 200, message = "接口名称长度不能超过 200")
    private String name;

    /** mode=create 时目标模块 */
    private UUID moduleId;

    /** mode=attach 时目标接口 */
    private UUID interfaceId;

    /** mode=attach 必填，目标接口当前变更版本号（乐观锁） */
    private Integer changeVersion;

    /** 调用方从 UI 表单构建的请求快照（method/url/headers/params/body），取代原先从 debug record 读取 */
    private Map<String, Object> request;

    /** 响应示例（status/headers/body），有响应时由前端填充 */
    private Map<String, Object> responseExample;
}
