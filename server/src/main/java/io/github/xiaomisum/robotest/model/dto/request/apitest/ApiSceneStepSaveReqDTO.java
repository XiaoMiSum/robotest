package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创建/更新场景步骤（测试场景详细设计 3.3.1/3.3.4）
 */
@Data
public class ApiSceneStepSaveReqDTO {

    @NotBlank
    private String name;

    /** http / jdbc，V1.2 仅 http 可执行 */
    private String stepType;

    private Integer sortOrder;

    private Boolean enabled;

    /** system/custom/public_step/copy/link，缺省 custom */
    private String sourceType;

    private UUID sourceId;

    /** {method, url, headers[], params[], body{type, content}, timeout} */
    private Map<String, Object> requestConfig;

    /** 步骤级处理器，结构与 Ryze 元件一致 */
    private List<Map<String, Object>> processors;

    /** [{id, name, enabled, target, condition, expected, expression}] */
    private List<Map<String, Object>> validators;

    /** [{id, name, enabled, source, expression, variableName}] */
    private List<Map<String, Object>> extractors;

}
