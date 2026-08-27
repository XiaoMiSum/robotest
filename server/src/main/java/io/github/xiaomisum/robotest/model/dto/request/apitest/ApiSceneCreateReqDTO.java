package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创建场景（测试场景详细设计 3.1.3）
 */
@Data
public class ApiSceneCreateReqDTO {

    @NotBlank
    @Size(max = 200)
    private String name;

    private UUID moduleId;

    private String description;

    /** 默认执行环境 */
    private UUID environmentId;

    @Valid
    private List<ApiSceneVariableBatchReqDTO.Variable> variables;

    /** 场景级处理器，结构与 Ryze 元件一致 */
    private List<Map<String, Object>> processors;

    /** all / continue，缺省 all */
    private String failureRule;

    /** {sharedEnabled, items:[{id, key, value, enabled, domain}]} */
    private Map<String, Object> cookieConfig;

}
