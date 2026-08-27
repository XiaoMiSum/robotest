package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.CommonComponentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentCopyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentIdRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.CommonComponentListItemRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface CommonComponentService {

    PageResult<CommonComponentListItemRespDTO> fetchList(UUID workspaceId, UUID projectId, UUID userId,
                                                         PageParam pageParam, String type, Boolean enabled,
                                                         String scope, String keyword);

    CommonComponentIdRespDTO create(UUID workspaceId, UUID projectId, UUID userId,
                                    CommonComponentSaveReqDTO reqDTO);

    void update(UUID workspaceId, UUID projectId, UUID userId, UUID id,
                CommonComponentSaveReqDTO reqDTO);

    void toggle(UUID workspaceId, UUID projectId, UUID userId, UUID id, boolean enabled);

    void batchToggle(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids, boolean enabled);

    void delete(UUID workspaceId, UUID projectId, UUID userId, UUID id);

    void batchDelete(UUID workspaceId, UUID projectId, UUID userId, List<UUID> ids);

    CommonComponentCopyRespDTO copy(UUID workspaceId, UUID projectId, UUID userId, UUID id);
}
