package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

/**
 * AI 全局配置（单例行，详设 2.2）：行缺失时按内置默认读取（enabled = false）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseUuidDO<AiConfig> {

    /** AI 总开关：关闭后新提交拒绝（1000018101），业务端入口隐藏 */
    private Boolean enabled;

    /** 默认模型（逻辑外键 → ai_model_config），可用模型解析的唯一依据 */
    private UUID defaultModelId;

    /** 单任务超时秒数，超时清扫器据此扫描（详设 4.2） */
    private Integer taskTimeoutSeconds;

    /** 模型调用失败的自动短重试次数（同 model_id） */
    private Integer taskMaxRetries;
}
