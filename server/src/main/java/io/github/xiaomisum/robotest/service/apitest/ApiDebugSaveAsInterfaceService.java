package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;

import java.util.UUID;

/**
 * 调试记录保存为接口（快速调试详细设计 4.3）
 */
public interface ApiDebugSaveAsInterfaceService {

    UUID saveAsInterface(UUID projectId, UUID workspaceId, UUID userId, UUID recordId,
                         ApiDebugSaveAsInterfaceReqDTO reqDTO);
}
