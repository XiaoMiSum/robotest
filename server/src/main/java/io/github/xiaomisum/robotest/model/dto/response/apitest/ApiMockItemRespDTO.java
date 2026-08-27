package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mock 列表项（Mock服务详细设计 3.1.1） */
@Data
public class ApiMockItemRespDTO {

    private UUID id;
    private String name;
    private UUID interfaceId;
    private String method;
    private String path;
    private Integer priority;
    private Boolean enabled;
    private Boolean followApi;
    private Integer responseStatus;
    private Long hitCount;
    private LocalDateTime lastHitAt;
    private LocalDateTime updatedAt;

}
