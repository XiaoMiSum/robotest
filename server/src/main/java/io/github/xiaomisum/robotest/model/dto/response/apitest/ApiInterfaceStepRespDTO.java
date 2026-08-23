package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 公共步骤条目（接口管理详细设计 3.1.2 steps / 3.2） */
@Data
@Builder
public class ApiInterfaceStepRespDTO {

    private UUID id;
    private String name;
    private String stepType;
    private Integer sortOrder;
    private Boolean enabled;
    private Map<String, Object> requestConfig;
    private List<Map<String, Object>> processors;
    private List<Map<String, Object>> validators;
    private List<Map<String, Object>> extractors;
}
