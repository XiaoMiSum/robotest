package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 场景关联接口（测试场景详细设计 2.1.6）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_scene_interface", autoResultMap = true)
public class ApiSceneInterface extends BaseUuidDO<ApiSceneInterface> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sceneId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    /** copy=快照独立 / link=跟随源变更 */
    private String syncMode;
    /** link 模式最近一次同步时间 */
    private LocalDateTime lastSyncedAt;

}
