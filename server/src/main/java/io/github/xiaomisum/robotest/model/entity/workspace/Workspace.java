package io.github.xiaomisum.robotest.model.entity.workspace;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_workspace")
public class Workspace extends BaseUuidDO<Workspace> {

    private String name;
    private String description;
    private String status;
    /** 创建人 user id（V1.2），历史数据可为 null */
    private UUID createdBy;
}
