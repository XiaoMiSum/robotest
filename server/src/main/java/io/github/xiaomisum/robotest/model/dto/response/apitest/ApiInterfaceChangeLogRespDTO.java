package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** 变更历史条目（接口管理详细设计 3.1.13），按版本号倒序 */
@Data
@Builder
public class ApiInterfaceChangeLogRespDTO {

    private UUID id;
    private Integer changeVersion;
    private String action;
    private String summary;
    private UUID operatorId;
    private LocalDateTime createdAt;
}
