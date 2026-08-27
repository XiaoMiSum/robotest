package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 场景关注关系（用户 ↔ 场景多对多，逻辑删除）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_scene_follow")
public class ApiSceneFollow extends BaseUuidDO<ApiSceneFollow> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;

}
