package io.github.xiaomisum.robotest.model.entity.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.List;
import java.util.Map;

/**
 * 工具注册表（详设 2.6.5）：执行期 AI 可调用工具的白名单与参数结构；
 * 写库与出网副作用禁止注册（side_effect 仅 readonly / confirmation）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_task_tool", autoResultMap = true)
public class AiTaskTool extends BaseUuidDO<AiTaskTool> {

    /** 工具标识（代码注册，随迁移种子数据落库） */
    private String toolCode;

    /** 工具名称（轨迹与确认闸门展示用） */
    private String name;

    /** 适用任务类型数组（白名单签发依据；["*"] 表示全部类型） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> taskTypes;

    /** 参数结构声明（执行前按此校验） */
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> paramSchema;

    /** 调用所需权限点（null = 继承发起人权限），执行期服务端鉴权 */
    private String permissionCode;

    /** readonly / confirmation（详设 2.6.5） */
    private String sideEffect;

    /** 停用后不出现在任何白名单 */
    private Boolean enabled;
}
