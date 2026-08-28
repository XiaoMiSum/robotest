package io.github.xiaomisum.robotest.model.entity.apitest;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("api_environment_variable")
public class ApiEnvironmentVariable extends BaseUuidDO<ApiEnvironmentVariable> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID environmentId;
    /** 变量名：仅字母/数字/下划线，同环境内唯一 */
    private String name;
    /** 变量取值：明文存储（详细设计 3.1.9） */
    private String value;
    private String description;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceStepId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID sourceReportId;
}
