package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 代码仓库配置（GitLab 集成详细设计 2.1.1），一项目一仓库。
 * <p>
 * repo_path / default_branch / import_mode 创建后不可修改（1000017711），
 * 变更绑定仓库须删除后重建；token_cipher 只进不出，不下发前端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_git_repo", autoResultMap = true)
public class ApiGitRepo extends BaseUuidDO<ApiGitRepo> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String name;
    private String gitlabBaseUrl;
    /** 项目路径（group/subgroup/repo），源侧稳定键，创建后不可改 */
    private String repoPath;
    /** 默认分支，创建后不可改 */
    private String defaultBranch;
    /** 导入模式：platform / gitlab_ci，创建后不可改 */
    private String importMode;
    /** 访问令牌密文（AES-256-GCM，只进不出） */
    private String tokenCipher;
    private String testSourceDir;
    private String resourceDir;
    private String envFilePath;
    /** 重复导入是否以源侧内容覆盖平台侧场景内容（默认不覆盖，仅 platform 模式生效） */
    private Boolean reimportOverwrite;
    private Boolean enabled;
    private LocalDateTime lastImportAt;
    /** 上次导入分支 HEAD，commit 幂等闸门比对基准 */
    private String lastImportCommit;
    /** 上次导入结果 {created,updated,parseFailed,errorFailed,skipped}，闸门只看 errorFailed */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> lastImportSummary;
    /** 源探查回写的默认分支最新提交，与 last_import_commit 不一致即「源有更新」 */
    private String headCommit;
    private LocalDateTime headCheckedAt;
    /** 源探查结果：ok / unreachable，unreachable 时保留上次 head_commit */
    private String headProbeStatus;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;

}
