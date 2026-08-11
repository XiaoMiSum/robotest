package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 智能体提示词模板（默认模板初始化时全量落库，页面可查看并修改；恢复默认即逻辑删除该行）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_prompt_template")
public class AiPromptTemplate extends BaseUuidDO<AiPromptTemplate> {

    private String functionType;
    private String roleInstruction;
    private String formatConstraint;
    private Boolean formatEditable;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
