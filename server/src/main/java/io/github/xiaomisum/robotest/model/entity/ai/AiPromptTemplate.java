package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.List;
import java.util.Map;

/**
 * 场景提示词（详设 2.5）：scene 与任务 type 对应；无自定义行时用处理器内置默认（source = default）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_prompt_template", autoResultMap = true)
public class AiPromptTemplate extends BaseUuidDO<AiPromptTemplate> {

    private String scene;

    private String name;

    /** 模板正文，支持 {{variable}} 占位 */
    private String content;

    /** 可用变量清单 [{ name, desc, required }] */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<Map<String, Object>> variables;

    /** default（内置默认）/ custom（自定义覆盖） */
    private String source;

    private Integer version;
}
