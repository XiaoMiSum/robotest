package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GitLabRepoSaveReqDTO {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称不能超过 100 字符")
    private String name;

    @NotBlank(message = "仓库地址不能为空")
    @Size(max = 500, message = "仓库地址不能超过 500 字符")
    private String repoUrl;

    @Size(max = 1000, message = "访问令牌不能超过 1000 字符")
    private String accessToken;

    @Size(max = 200, message = "分支名称不能超过 200 字符")
    private String branch = "main";

    @Size(max = 500, message = "测试源码路径不能超过 500 字符")
    private String testSourcePath;
}
