package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 创建接口定义（接口管理详细设计 3.1.3）；V1.2 仅 http 协议
 */
@Data
public class ApiInterfaceCreateReqDTO {

    @NotBlank(message = "接口名称不能为空")
    @Size(max = 200, message = "接口名称长度不能超过 200")
    private String name;

    /** 缺省 http；jdbc 随场景模块开放 */
    private String protocol = "http";

    @Size(max = 10, message = "HTTP 方法长度不能超过 10")
    private String method;

    @Size(max = 500, message = "路径长度不能超过 500")
    private String path;

    @Size(max = 200, message = "描述长度不能超过 200")
    private String description;

    private UUID moduleId;

    private List<Map<String, Object>> headers;

    private Map<String, Object> body;

    private List<Map<String, Object>> params;

    private List<Map<String, Object>> restParams;

    private Map<String, Object> auth;

    private String status;

    private Map<String, Object> responseExample;
}
