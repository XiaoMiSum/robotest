package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行报告（API测试基础设施详细设计 2.1.4）；查询/分享 API 于报告迭代交付
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_report", autoResultMap = true)
public class ApiReport extends BaseUuidDO<ApiReport> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executionRecordId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    /** 执行时固化快照 */
    private String sceneName;
    private String environmentName;
    private String executionMode;
    /** success / failed / partial */
    private String status;
    /** {total, passed, failed, skipped, durationMs} */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> summary;
    /** 步骤级结果明细 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> stepResults;
    /** Ryze 标准 JSON 快照，结果回溯用 */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> ryzeSnapshot;
    private Boolean shareEnabled;
    private String shareToken;
    private LocalDateTime shareExpiresAt;

}
