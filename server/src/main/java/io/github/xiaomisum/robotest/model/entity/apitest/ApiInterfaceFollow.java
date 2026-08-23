package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 接口关注关系（接口管理详细设计 2.1.6），取消关注即逻辑删除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_interface_follow")
public class ApiInterfaceFollow extends BaseUuidDO<ApiInterfaceFollow> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID userId;
}
