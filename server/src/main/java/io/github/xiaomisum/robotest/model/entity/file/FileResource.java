package io.github.xiaomisum.robotest.model.entity.file;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.mybatis.core.dataobject.BaseUuidDO;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.UUID;

/**
 * 泛化附件资源（文件管理详设 2.1）：不挂工作空间 / 项目，使用方以 ID 或访问 URL 关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_resource")
public class FileResource extends BaseUuidDO<FileResource> {

    /** 对象存储对象键（服务端生成 objects/{uuid}{ext}，不含用户可控路径） */
    private String objectKey;

    /** 原始文件名（仅存库，不进对象键） */
    private String fileName;

    private String contentType;

    private Long fileSize;

    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID uploaderId;
}
