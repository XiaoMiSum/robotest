package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug_log")
public class BugLog extends BaseUuidDO<BugLog> {

    private String bugId;
    private String operatorId;
    private String operationType;
    private String content;
}
