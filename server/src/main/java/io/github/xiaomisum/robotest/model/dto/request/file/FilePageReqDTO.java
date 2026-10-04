package io.github.xiaomisum.robotest.model.dto.request.file;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.common.pojo.PageParam;

/**
 * 文件分页查询（GET /api/files，文件管理详设 4.1），筛选条件可选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FilePageReqDTO extends PageParam {

    /** 文件名模糊过滤 */
    private String fileName;
}
