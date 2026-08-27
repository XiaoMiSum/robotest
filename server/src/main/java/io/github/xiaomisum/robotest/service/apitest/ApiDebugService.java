package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugExecuteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugRenameReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiDebugSaveAsInterfaceReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugCurlImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugExecuteRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRecordItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiDebugRestoreRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

public interface ApiDebugService {

    ApiDebugExecuteRespDTO execute(UUID projectId, UUID workspaceId, UUID userId, ApiDebugExecuteReqDTO reqDTO);

    ApiDebugCurlImportRespDTO importCurl(UUID projectId, UUID workspaceId, UUID userId, String curl);

    PageResult<ApiDebugRecordItemRespDTO> pageRecords(UUID projectId, UUID workspaceId, UUID userId,
            String keyword, PageParam pageParam);

    void deleteRecord(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    void renameRecord(UUID projectId, UUID workspaceId, UUID userId, UUID id, ApiDebugRenameReqDTO reqDTO);

    ApiDebugRestoreRespDTO restore(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    UUID saveAsInterface(UUID projectId, UUID workspaceId, UUID userId, UUID id,
            ApiDebugSaveAsInterfaceReqDTO reqDTO);
}
