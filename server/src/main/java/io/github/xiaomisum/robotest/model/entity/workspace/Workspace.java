package io.github.xiaomisum.robotest.model.entity.workspace;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_workspace")
public class Workspace extends BaseUuidDO<Workspace> {

    private String name;
    private String description;
    private String status;
}
