package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabSyncConfigReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabMetadataListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncConfigRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabSyncHistoryItemRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface GitLabMetadataImportService {

    GitLabMetadataImportRespDTO importMetadata(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId);

    PageResult<GitLabMetadataListItemRespDTO> fetchMetadataPage(UUID projectId, UUID workspaceId, UUID userId,
                                                                 UUID repositoryId, Boolean isExecutable,
                                                                 String keyword, PageParam pageParam);

    GitLabMetadataImportRespDTO syncMetadata(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId);

    List<GitLabSyncHistoryItemRespDTO> fetchSyncHistory(UUID projectId, UUID workspaceId, UUID userId,
                                                         UUID repositoryId);

    GitLabSyncConfigRespDTO fetchSyncConfig(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId);

    boolean updateSyncConfig(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId,
                              GitLabSyncConfigReqDTO config);
}
