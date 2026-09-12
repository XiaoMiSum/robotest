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
 * 执行报告（API测试基础设施详细设计 2.1.4）；查询/分享 API 于报告迭代交付
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_report", autoResultMap = true)
public class ApiReport extends BaseUuidDO<ApiReport> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** 关联执行记录：场景报告 1:1；套件报告对应多条执行记录，此字段置空 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID executionRecordId;
    /** 报告粒度：scene（场景报告）/ suite（套件报告，定时任务含立即执行聚合多场景） */
    private String reportType;
    /** 外部关联 ID 随 report_type：suite=任务 ID；scene=场景 ID */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID externalId;
    /** 报告名称（执行时固化）：场景报告=场景名+时间戳；套件报告=任务名+时间戳 */
    private String name;
    private String environmentName;
    private String executionMode;
    /** 报告来源：scene（场景页运行）/ schedule（定时任务含立即执行）。scene 不进报告列表 */
    private String source;
    /** success / failed / partial（scene）；套件按整体判定 */
    private String status;
    /** 结果汇总（场景报告 {total, passed, failed, skipped, durationMs}；套件报告含场景级汇总） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> summary;
    /** 结果明细数据集：按 reportType 构建场景数据集 / 套件数据集（测试报告详细设计 2.3） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> result;
    /** Ryze 标准结果树快照，结果回溯用（仅平台内执行保留） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> ryzeSnapshot;
    private String shareToken;
    private LocalDateTime shareExpiresAt;
    /** 分享者（最后一次生成分享链接的用户），随分享记录展示与复制文本（测试报告详细设计 4.2.3） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID shareUserId;

}