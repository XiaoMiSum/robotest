package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 调试记录保存为接口定义的响应（快速调试详细设计 3.1.3）
 */
@Data
@Builder
public class ApiDebugSaveAsInterfaceRespDTO {

    private UUID interfaceId;
}
