package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 连通性测试请求（POST /api/project/git-repos/connections/test，GitLab 集成详细设计 3.1）。
 * <p>
 * 不依赖 repoId，保存前的空态亦可调用；body 字段缺省时回落已存配置（一项目一仓库按 projectId 取），不落库。
 */
@Data
public class ApiGitRepoConnectionTestReqDTO {

    @Pattern(regexp = "https?://.+", message = "GitLab 地址仅支持 http/https 且结尾不留 /")
    @Size(max = 500, message = "GitLab 地址不能超过 500 字符")
    private String gitlabBaseUrl;

    @Size(max = 500, message = "项目路径不能超过 500 字符")
    private String repoPath;

    @Size(max = 200, message = "默认分支不能超过 200 字符")
    private String defaultBranch;

    @Size(max = 500, message = "访问令牌不能超过 500 字符")
    private String token;

}
