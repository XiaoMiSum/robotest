package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;

import java.util.UUID;

/**
 * 导入与解析（GitLab 集成详细设计 3.2，批次一 WP-3.2）。
 */
public interface ApiGitImportService {

    /** 扫描预览：拉取目标分支、扫描测试类并解析套件，返回将创建 / 更新 / 解析失败的清单与汇总，不落库 */
    ApiGitImportPreviewRespDTO preview(UUID projectId, UUID workspaceId, UUID userId, UUID repoId);

    /** 执行导入：并发闸门同步判定，拉取与解析异步执行，导入写库为单事务（BR-1.15） */
    ApiGitImportStatusRespDTO imports(UUID projectId, UUID workspaceId, UUID userId, UUID repoId);

    /** 查询当前仓库导入进度（阶段、已处理数、总量） */
    ApiGitImportStatusRespDTO status(UUID projectId, UUID workspaceId, UUID userId, UUID repoId);
}
