package io.github.xiaomisum.robotest.model.dto.response.file;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件资源信息（上传响应与列表行共用，文件管理详设 4.2）。
 */
@Data
public class FileResourceRespDTO {

    private UUID id;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private UUID uploaderId;

    /** 列表查询回填（上传响应为 null） */
    private String uploaderName;

    /** 稳定访问地址（相对路径，使用方按此关联，详设 3.3）；下载时前端带鉴权取流 */
    private String downloadUrl;

    private LocalDateTime createdAt;
}
