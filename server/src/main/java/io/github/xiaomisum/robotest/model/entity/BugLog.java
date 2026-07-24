package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug_log")
public class BugLog extends BaseUuidDO<BugLog> {

    private UUID bugId;
    private UUID operatorId;
    private String operationType;
    private String content;
}
