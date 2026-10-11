package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 连通性测试响应（GitLab 集成详细设计 3.1）。
 * <p>
 * 仅在全部检查通过时返回；任一不满足抛 1000017702，失败原因区分实例不可达 / 令牌无效 / 分支不存在（需求 BR-1.5）。
 */
@Data
@AllArgsConstructor
public class ApiGitRepoConnectionTestRespDTO {

    private Boolean reachable;
    private Boolean tokenValid;
    private Boolean branchExists;
    /** GitLab 实例版本号 */
    private String version;
    /** 目标分支实际分支名 */
    private String resolvedBranch;

}
