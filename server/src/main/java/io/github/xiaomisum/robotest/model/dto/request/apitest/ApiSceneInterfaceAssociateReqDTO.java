package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/** 批量关联接口（测试场景详细设计 3.2.2），同一接口不允许重复关联（7208） */
@Data
public class ApiSceneInterfaceAssociateReqDTO {

    private java.util.List<UUID> interfaceIds;

    /** copy=快照独立（默认）/ link=链接跟随 */
    private String syncMode;

}
