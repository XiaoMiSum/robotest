package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabExecutableImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabExecutableImportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabFileTreeNodeRespDTO;

import java.util.List;
import java.util.UUID;

public interface GitLabExecutableImportService {

    GitLabFileTreeNodeRespDTO browseFiles(UUID projectId, UUID workspaceId, UUID userId,
                                           UUID repositoryId, String path);

    GitLabExecutableImportRespDTO importExecutable(UUID projectId, UUID workspaceId, UUID userId,
                                                     UUID repositoryId, GitLabExecutableImportReqDTO reqDTO);

    GitLabExecutableImportRespDTO fetchLatestImport(UUID projectId, UUID workspaceId, UUID userId,
                                                      UUID repositoryId);
}
