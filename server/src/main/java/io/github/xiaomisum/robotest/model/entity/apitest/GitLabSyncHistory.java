package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableName;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_gitlab_sync_history", autoResultMap = true)
public class GitLabSyncHistory extends BaseUuidDO<GitLabSyncHistory> {
    private UUID repositoryId;
    private LocalDateTime syncAt;
    private Integer classCount;
    private Integer methodCount;
    private String commitSha;
    private String status;
}
