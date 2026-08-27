package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_gitlab_repository")
public class GitLabRepository extends BaseUuidDO<GitLabRepository> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String name;
    private String repoUrl;
    private String branch;
    private String accessTokenCipher;
    private String tokenSuffix;
    private String testSourcePath;
    private String lastImportStatus;
    private LocalDateTime lastImportAt;
    private LocalDateTime lastMetadataSyncAt;
    private String lastCommitSha;
    private Boolean autoSyncEnabled;
    private String annotationFilter;
    private Boolean onlyWithResourcePath;
}
