package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 接口定义详情（接口管理详细设计 3.1.2），含完整请求参数模型与公共步骤 */
@Data
@Builder
public class ApiInterfaceDetailRespDTO {

    private UUID id;
    private String name;
    private String protocol;
    private String method;
    private String path;
    private String description;
    private UUID moduleId;
    private List<Map<String, Object>> headers;
    /** {type, content} */
    private Map<String, Object> body;
    private List<Map<String, Object>> params;
    private List<Map<String, Object>> restParams;
    private Map<String, Object> auth;
    private String status;
    private Integer changeVersion;
    /** {status, headers, body} */
    private Map<String, Object> responseExample;
    private Integer referenceCount;
    private Boolean followed;
    private List<ApiInterfaceStepRespDTO> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
