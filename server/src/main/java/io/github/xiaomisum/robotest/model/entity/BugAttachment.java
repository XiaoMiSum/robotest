package io.github.xiaomisum.robotest.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bug_attachment")
public class BugAttachment extends BaseUuidDO<BugAttachment> {

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID bugId;
    private String fileName;
    private String storagePath;
    private Long fileSize;
    private String contentType;
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID uploaderId;
}
