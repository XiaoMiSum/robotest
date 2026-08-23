package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "api_data_source", autoResultMap = true)
public class ApiDataSource extends BaseUuidDO<ApiDataSource> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    private String name;
    /** 引用名称（对应 Ryze ref_name，步骤中引用该数据源） */
    private String refName;
    private String driver;
    /** JDBC 连接 URL（用户名密码直接写入 URL，不独立存储） */
    private String url;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> connectionProperties;
    private Integer maxPoolSize;
}
