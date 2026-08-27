package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mock 详情（Mock服务详细设计 3.1.2），含完整匹配规则与响应定义 */
@Data
public class ApiMockDetailRespDTO {

    private UUID id;
    private String name;
    private UUID interfaceId;
    private String interfaceName;
    private String method;
    private String path;
    private Integer priority;
    private String description;
    private List<Map<String, Object>> matchRules;
    private Boolean enabled;
    private Boolean followApi;
    private Integer responseStatus;
    private Map<String, Object> responseHeaders;
    private String responseBodyType;
    private String responseBody;
    private Integer delayMs;
    private Long hitCount;
    private LocalDateTime lastHitAt;
    /** 同路径同方法组内规则总数，前端用于上移/下移按钮置灰（跨组不可移动） */
    private Integer groupSize;

}
