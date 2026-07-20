package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseDO;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug_log")
public class BugLog extends BaseDO {

    @TableId
    private String id;
    private String bugId;
    private String operatorId;
    private String operationType;
    private String content;
}
