package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiParsedImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;

import java.util.UUID;

/**
 * 接口导入服务（详细设计 4.1）
 */
public interface ApiInterfaceImportService {

    ApiImportResultRespDTO importParsed(UUID projectId, UUID userId, ApiParsedImportReqDTO reqDTO);

    ApiImportResultRespDTO importUrl(UUID projectId, UUID userId, String url, String formatHint);

    ApiImportPreviewRespDTO preview(UUID projectId, UUID userId, String url, String formatHint);
}
