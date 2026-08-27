package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** 场景关联接口条目（测试场景详细设计 3.2.1） */
@Data
@Builder
public class ApiSceneAssociationItemRespDTO {

    private UUID id;

    private UUID interfaceId;

    private String interfaceName;

    private String method;

    private String path;

    /** copy / link */
    private String syncMode;

    /** 该接口下公共步骤数量 */
    private Integer publicStepCount;

    private LocalDateTime createdAt;

}
