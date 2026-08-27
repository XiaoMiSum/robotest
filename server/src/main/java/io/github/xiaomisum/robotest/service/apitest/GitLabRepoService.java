package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabRepoSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoListItemRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabRepoTestConnectionRespDTO;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

public interface GitLabRepoService {

    PageResult<GitLabRepoListItemRespDTO> fetchPage(UUID projectId, UUID workspaceId, UUID userId,
                                                     String keyword, PageParam pageParam);

    UUID create(UUID projectId, UUID workspaceId, UUID userId, GitLabRepoSaveReqDTO reqDTO);

    void update(UUID projectId, UUID workspaceId, UUID userId, UUID id, GitLabRepoSaveReqDTO reqDTO);

    void delete(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    GitLabRepoTestConnectionRespDTO testConnection(UUID projectId, UUID workspaceId, UUID userId, UUID id);

    List<String> listBranches(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId);
}
