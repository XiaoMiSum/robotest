package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiGitRepo;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ApiGitRepoMapper extends BaseMapperX<ApiGitRepo> {

    /** 一项目一仓库（uk_api_git_repo_project），按项目取唯一配置 */
    default ApiGitRepo selectByProject(UUID projectId) {
        return selectOne(new LambdaQueryWrapperX<ApiGitRepo>()
                .eq(ApiGitRepo::getProjectId, projectId));
    }

    /** 源探查作业输入：全部启用中的仓库（GitLab 集成详设 3.7），逻辑删除行由 @TableLogic 过滤 */
    default List<ApiGitRepo> selectEnabledRepos() {
        return selectList(new LambdaQueryWrapperX<ApiGitRepo>()
                .eq(ApiGitRepo::getEnabled, true));
    }

    /**
     * 探查结果回写：仅更新探查三列（C11 部分更新）。
     * headCommit 为 null 表示本次探查失败，不触碰该列以保留上次提交号（详设 3.7 第 3 步）。
     */
    default int updateProbeResult(UUID id, String headCommit, LocalDateTime headCheckedAt, String probeStatus) {
        // set() 返回父类型，故以语句式调用（wrapper 本身可变），避免链式赋值丢类型
        LambdaUpdateWrapperX<ApiGitRepo> wrapper = new LambdaUpdateWrapperX<>();
        wrapper.eq(ApiGitRepo::getId, id)
                .set(ApiGitRepo::getHeadCheckedAt, headCheckedAt)
                .set(ApiGitRepo::getHeadProbeStatus, probeStatus);
        if (headCommit != null) {
            wrapper.set(ApiGitRepo::getHeadCommit, headCommit);
        }
        return update(null, wrapper);
    }

}
