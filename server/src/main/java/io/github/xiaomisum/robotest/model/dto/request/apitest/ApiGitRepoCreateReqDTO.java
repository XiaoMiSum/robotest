package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建代码仓库配置请求（POST /api/project/git-repos，GitLab 集成详细设计 3.1）。
 * <p>
 * 一项目一仓库：重复绑定同一项目返回 1000017703；
 * repoPath / defaultBranch / importMode 创建后锁定，变更须删除后重建（1000017711）。
 */
@Data
public class ApiGitRepoCreateReqDTO {

    @NotBlank(message = "名称不能为空")
    @Size(max = 200, message = "名称不能超过 200 字符")
    private String name;

    @NotBlank(message = "GitLab 地址不能为空")
    @Pattern(regexp = "https?://.+", message = "GitLab 地址仅支持 http/https 且结尾不留 /")
    @Size(max = 500, message = "GitLab 地址不能超过 500 字符")
    private String gitlabBaseUrl;

    /** 形如 group/subgroup/repo；格式校验在 Service 层执行（不含 .git 后缀与 http(s):// 前缀） */
    @Size(max = 500, message = "项目路径不能超过 500 字符")
    private String repoPath;

    /** 缺省 master */
    @Size(max = 200, message = "默认分支不能超过 200 字符")
    private String defaultBranch;

    /** platform / gitlab_ci，取值校验在 Service 层执行 */
    @NotBlank(message = "接入方式不能为空")
    @Size(max = 20, message = "接入方式不能超过 20 字符")
    private String importMode;

    /** 只写不读，非空校验与连通性实测在 Service 层执行 */
    @Size(max = 500, message = "访问令牌不能超过 500 字符")
    private String token;

    /** 缺省 src/test/java */
    @Size(max = 500, message = "测试源码目录不能超过 500 字符")
    private String testSourceDir;

    /** 缺省 src/test/resources */
    @Size(max = 500, message = "资源目录不能超过 500 字符")
    private String resourceDir;

    /** 缺省 common/env/env.yaml */
    @Size(max = 500, message = "环境声明文件路径不能超过 500 字符")
    private String envFilePath;

}
