package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockBatchToggleReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockDebugReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiMockSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockAddressRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockBatchToggleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDebugRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiMockMoveRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

/**
 * Mock 管理（Mock服务详细设计 3.1/3.2）；免登录访问入口见 MockAccessFilter
 */
public interface ApiMockService {

    PageResult<ApiMockItemRespDTO> fetchPage(UUID workspaceId, UUID projectId, UUID userId,
                                             UUID interfaceId, String search, Boolean enabled,
                                             PageParam pageParam);

    ApiMockDetailRespDTO getDetail(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    ApiMockIdRespDTO create(UUID workspaceId, UUID projectId, UUID userId, ApiMockSaveReqDTO reqDTO);

    /** 继承接口定义的 path/method 并落关联（详细设计 3.1.4），请求体仅提供名称与响应等可编辑项 */
    ApiMockIdRespDTO createFromInterface(UUID workspaceId, UUID projectId, UUID userId,
                                         UUID interfaceId, ApiMockSaveReqDTO reqDTO);

    void update(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiMockSaveReqDTO reqDTO);

    void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled);

    ApiMockBatchToggleRespDTO batchToggle(UUID workspaceId, UUID projectId, UUID userId,
                                          ApiMockBatchToggleReqDTO reqDTO);

    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    /** 复制为「- 副本」并默认停用，避免与源规则地址冲突（详细设计 3.1.10） */
    ApiMockIdRespDTO duplicate(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    void resetHitCount(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    ApiMockAddressRespDTO getAddress(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    /** 模拟命中一次，不计入命中统计、不写访问日志（详细设计 3.2.1） */
    ApiMockDebugRespDTO debug(UUID workspaceId, UUID projectId, UUID userId, UUID id, ApiMockDebugReqDTO reqDTO);

    /** 仅同路径同方法组内移动，与相邻规则交换优先级序号（详细设计 3.1.12） */
    ApiMockMoveRespDTO moveUp(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    ApiMockMoveRespDTO moveDown(UUID workspaceId, UUID projectId, UUID userId, UUID id);

}
