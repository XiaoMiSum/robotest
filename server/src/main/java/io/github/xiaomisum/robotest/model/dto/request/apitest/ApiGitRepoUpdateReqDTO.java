package io.github.xiaomisum.robotest.model.dto.request.apitest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 更新代码仓库配置请求（PUT /api/project/git-repos/{repoId}，GitLab 集成详细设计 3.1）。
 * <p>
 * 部分更新：仅更新实际传入字段；token 留空表示沿用已存令牌；
 * repoPath / defaultBranch / importMode 创建后锁定，不在请求体中接受，出现（含显式 null）即返回 1000017711。
 */
@Data
public class ApiGitRepoUpdateReqDTO {

    /** 创建后锁定的字段名，出现即拒绝；变更绑定仓库须删除后重建 */
    private static final Set<String> LOCKED_FIELDS = Set.of("repoPath", "defaultBranch", "importMode");

    @Size(max = 200, message = "名称不能超过 200 字符")
    private String name;

    @Pattern(regexp = "https?://.+", message = "GitLab 地址仅支持 http/https 且结尾不留 /")
    @Size(max = 500, message = "GitLab 地址不能超过 500 字符")
    private String gitlabBaseUrl;

    /** 编辑态留空表示不修改，只写不读 */
    @Size(max = 500, message = "访问令牌不能超过 500 字符")
    private String token;

    @Size(max = 500, message = "测试源码目录不能超过 500 字符")
    private String testSourceDir;

    @Size(max = 500, message = "资源目录不能超过 500 字符")
    private String resourceDir;

    @Size(max = 500, message = "环境声明文件路径不能超过 500 字符")
    private String envFilePath;

    /** 锁定字段出现检测（含显式 null 与未知字段），Service 按键名拒绝 */
    private Map<String, Object> rejectedFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void setRejectedFields(String key, Object value) {
        rejectedFields.put(key, value);
    }

}
