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
@TableName("api_function")
public class ApiFunction extends BaseUuidDO<ApiFunction> {

    /** 函数类型：builtin（Ryze 内置元数据）/ custom（用户创建的自定义函数） */
    private String type;
    /** 作用域：project（项目）/ workspace（空间）/ global（全局） */
    private String scope;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID workspaceId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID projectId;
    /** 函数名，调用语法 ${名称(参数...)}；同作用域唯一 */
    private String name;
    private String description;
    /** 参数说明（函数助手展示用） */
    private String paramsDesc;
    /** Groovy 脚本体，args 数组承接调用参数，返回值即求值结果 */
    private String script;
    private Boolean enabled;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID updatedBy;
}
