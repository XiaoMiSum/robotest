package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoConnectionTestReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoConnectionTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoRespDTO;

import java.util.UUID;

/**
 * 代码仓库配置（GitLab 集成详细设计 3.1），一项目一仓库。
 */
public interface ApiGitRepoService {

    /** 查询当前项目仓库配置，未配置时返回 null（前端呈现空态引导） */
    ApiGitRepoRespDTO getRepo(UUID projectId, UUID workspaceId, UUID userId);

    /** 创建仓库配置；创建前强制实测连通性，失败不落库（1000017702） */
    ApiGitRepoRespDTO createRepo(UUID projectId, UUID workspaceId, UUID userId, ApiGitRepoCreateReqDTO reqDTO);

    /** 更新配置（部分更新）；锁定字段出现即拒绝（1000017711），变更绑定仓库须删除后重建 */
    void updateRepo(UUID projectId, UUID workspaceId, UUID userId, UUID repoId, ApiGitRepoUpdateReqDTO reqDTO);

    /** 逻辑删除；其下已导入场景保留但 source_status 置 repo_removed；存在启用中同步任务时拒绝（1000017712） */
    void deleteRepo(UUID projectId, UUID workspaceId, UUID userId, UUID repoId);

    /** 连通性测试：不依赖 repoId，body 缺省字段回落已存配置，不落库；全部通过才返回结果 */
    ApiGitRepoConnectionTestRespDTO testConnection(UUID projectId, UUID workspaceId, UUID userId,
            ApiGitRepoConnectionTestReqDTO reqDTO);

}
