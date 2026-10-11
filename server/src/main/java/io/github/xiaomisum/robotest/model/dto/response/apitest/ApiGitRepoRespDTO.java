package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 代码仓库配置响应（GitLab 集成详细设计 3.1）。
 * <p>
 * hasUpdates 为服务端派生（headCommit != lastImportCommit，任一为 NULL 时 false），前端不自行比对提交号；
 * tokenCipher 只进不出，永不出现在任何响应中。
 */
@Data
public class ApiGitRepoRespDTO {

    private String id;
    private String projectId;
    private String name;
    private String gitlabBaseUrl;
    private String repoPath;
    private String defaultBranch;
    /** platform / gitlab_ci */
    private String importMode;
    private Boolean enabled;
    private String testSourceDir;
    private String resourceDir;
    private String envFilePath;
    private LocalDateTime lastImportAt;
    private String lastImportCommit;
    /** 上次导入结果 {created,updated,parseFailed,errorFailed,skipped} */
    private Map<String, Object> lastImportSummary;
    private String headCommit;
    private LocalDateTime headCheckedAt;
    /** ok / unreachable */
    private String headProbeStatus;
    private Boolean hasUpdates;

}
