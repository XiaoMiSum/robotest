package io.github.xiaomisum.robotest.model.dto.response.file;

import lombok.Data;

/**
 * presigned 临时访问地址（GET /api/files/{id}/access-url，文件管理详设 4.2）。
 */
@Data
public class FileAccessUrlRespDTO {

    /** presigned URL，时效内可直连对象存储；禁止持久化进业务内容 */
    private String url;

    /** 有效期（秒） */
    private Integer expiresIn;
}
