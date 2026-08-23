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
    /** type=sensitive 时存 AES-256-GCM 密文，不输出明文至前端 */
    private String value;
    private String description;
    /** 变量类型：text / number / sensitive */
    private String type;
}
