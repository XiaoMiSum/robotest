package io.github.xiaomisum.robotest.model.entity.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug_log")
public class BugLog extends BaseUuidDO<BugLog> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID bugId;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID operatorId;
    private String operationType;
    private String content;
}
