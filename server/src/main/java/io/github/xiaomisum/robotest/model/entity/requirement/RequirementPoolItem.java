package io.github.xiaomisum.robotest.model.entity.requirement;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.xiaomisum.robotest.framework.common.Constants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 需求池条目（项目级轻量需求条目库，US-AI-004）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("requirement_pool_item")
public class RequirementPoolItem extends BaseUuidDO<RequirementPoolItem> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    private String title;
    // 需求文本，Markdown 原文存储
    private String content;
    private String sourceUrl;
    /** 条目状态：active / archived，默认 active（Constants.Status） */
    private String status = Constants.Status.ACTIVE;
    /** AI 拆分入库标识（US-AI-019，仅作展示标记，不影响业务规则） */
    private Boolean aiGenerated = false;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID createdBy;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
