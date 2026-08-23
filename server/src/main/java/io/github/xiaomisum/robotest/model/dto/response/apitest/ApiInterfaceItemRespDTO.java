package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** 接口列表条目（接口管理详细设计 3.1.1） */
@Data
@Builder
public class ApiInterfaceItemRespDTO {

    private UUID id;
    private String name;
    private String protocol;
    private String method;
    private String path;
    private UUID moduleId;
    /** enabled / disabled */
    private String status;
    private Integer referenceCount;
    private Integer changeVersion;
    /** 当前用户是否已关注（前端星标状态） */
    private Boolean followed;
    private LocalDateTime updatedAt;
}
