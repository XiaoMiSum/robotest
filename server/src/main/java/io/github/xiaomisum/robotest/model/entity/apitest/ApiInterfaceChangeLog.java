package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 接口变更历史（接口管理详细设计 2.1.7），每次保存生成一条并递增版本号
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_interface_change_log")
public class ApiInterfaceChangeLog extends BaseUuidDO<ApiInterfaceChangeLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID interfaceId;
    /** 与 api_interface.change_version 对应 */
    private Integer changeVersion;
    /** create / update / copy / import / status */
    private String action;
    private String summary;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID operatorId;
}
