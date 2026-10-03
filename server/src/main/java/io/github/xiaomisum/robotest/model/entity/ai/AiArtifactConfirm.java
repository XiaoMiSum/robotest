package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

/**
 * 产物确认记录（详设 2.7）：uk(task_id, artifact_key) 幂等兜底，确认/驳回动作的落库凭据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_artifact_confirm", autoResultMap = true)
public class AiArtifactConfirm extends BaseUuidDO<AiArtifactConfirm> {

    /** 落库目标项目（经 X-Active-Project 头传递，不出请求体） */
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID taskId;

    private String artifactKey;

    /** adopted / adopted_edited / rejected */
    private String action;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID operatorId;

    /** 采纳落库引用（生成的业务实体 ID 等） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> adoptedRef;

    /** 驳回说明 */
    private String note;
}
