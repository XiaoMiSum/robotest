package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchDeleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceBatchMoveReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceStatusReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiInterfaceUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiImportResultRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiInterfaceReferenceRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

/**
 * 接口定义管理（接口管理详细设计 3.1–3.4）
 */
public interface ApiInterfaceService {

    PageResult<ApiInterfaceItemRespDTO> page(UUID projectId, UUID workspaceId, UUID userId,
                                             UUID moduleId, String search, String status, String view,
                                             PageParam pageParam);

    ApiInterfaceDetailRespDTO getDetail(UUID projectId, UUID interfaceId, UUID userId);

    UUID create(UUID projectId, UUID workspaceId, UUID userId, ApiInterfaceCreateReqDTO reqDTO);

    void update(UUID projectId, UUID workspaceId, UUID userId, UUID id, ApiInterfaceUpdateReqDTO reqDTO);

    void delete(UUID projectId, UUID userId, UUID id);

    UUID copy(UUID projectId, UUID userId, UUID id, String copyName);

    ApiInterfaceReferenceRespDTO references(UUID projectId, UUID userId, UUID id);

    List<ApiInterfaceReferenceRespDTO.RefItem> referenceScenes(UUID projectId, UUID userId, UUID id);

    void batchMove(UUID projectId, UUID userId, ApiInterfaceBatchMoveReqDTO reqDTO);

    void batchDelete(UUID projectId, UUID userId, ApiInterfaceBatchDeleteReqDTO reqDTO);

    void updateStatus(UUID projectId, UUID userId, UUID id, ApiInterfaceStatusReqDTO reqDTO);

    void follow(UUID projectId, UUID userId, UUID id);

    void unfollow(UUID projectId, UUID userId, UUID id);

    // ==================== 导入 3.4 ====================

    ApiImportResultRespDTO importFile(UUID projectId, UUID userId, byte[] content,
                                      String filename, String formatHint);

    ApiImportResultRespDTO importUrl(UUID projectId, UUID userId, String url, String formatHint);

    ApiImportPreviewRespDTO preview(UUID projectId, UUID userId, byte[] content, String formatHint);

    // ==================== 变更历史 3.1.13 ====================

    PageResult<ApiInterfaceChangeLogRespDTO> changeLogs(UUID projectId, UUID userId, UUID interfaceId, PageParam pageParam);
}
