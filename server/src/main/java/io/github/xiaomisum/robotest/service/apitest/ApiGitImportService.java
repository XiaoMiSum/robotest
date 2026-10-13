package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;

import java.util.Map;
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

    /**
     * GitLab 同步任务的同步导入（GitLab 集成详设 3.6、定时任务详设 27 §4）：
     * 按项目取唯一仓库 → 分支 HEAD 幂等闸门判定 → 命中闸门不进解析直接返回 skipped；
     * 未命中走 3.5 导入流程（compare 改名迁移 + 稳定键 upsert + 单事务回写）。
     * 权限已在任务创建 / 手动触发入口校验，本方法为执行链路内部入口，不做成员校验；
     * 与手动导入互不排队（G2 / BR-1.36）：不占并发闸门、不进 importExecutor，靠 upsert 幂等保证结果一致。
     */
    SyncOutcome syncImport(UUID projectId, UUID userId);

    /**
     * 同步导入结果：skipped = 命中幂等闸门（无变更、不进解析）；
     * summary 恒非空——无变更时为全零计数且 skipped = 1（详设 27 §4 步骤 2），否则为本次导入汇总；
     * headCommit 为本次判定的目标分支 HEAD，由调用方并入执行记录 sync_summary.commit
     */
    record SyncOutcome(boolean skipped, String headCommit, Map<String, Object> summary) {
    }
}
