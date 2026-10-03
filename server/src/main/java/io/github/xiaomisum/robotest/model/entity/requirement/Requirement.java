package io.github.xiaomisum.robotest.model.entity.requirement;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 需求条目（域门面表，详设 2.2）：追溯链起点，不提供删除，归档为终态入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "requirement", autoResultMap = true)
public class Requirement extends BaseUuidDO<Requirement> {

    /** 隔离边界，查询强制过滤 */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    /** 归属模块（逻辑外键 → 项目统一模块树节点） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID moduleId;

    /** 被测业务系统版本，按属性变更处理（不触发状态流转与影响分析） */
    private String systemVersion;

    /** 需求编号 REQ-001，项目内唯一（分配规则见详设 4.1） */
    private String code;

    private String title;

    /** 需求描述正文（Markdown） */
    private String description;

    /** 状态机：draft / confirmed / changed / archived（Constants.RequirementStatus）；不设字段初值，避免部分更新载体误写状态 */
    private String status;

    /** 优先级：high / medium / low */
    private String priority;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID ownerId;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;

    /** 来源：manual / import（创建时显式赋值） */
    private String source;

    /** 导入来源附件 ID（文件管理模块承载，回看与下载见详设 3.8） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceFileId;

    /** 最近一次进入已确认状态的时间 */
    private LocalDateTime confirmedAt;
}
