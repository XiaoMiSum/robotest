package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 导入统计（环境导入 / 变量批量导入共用）：重名按 overwrite 开关覆盖或跳过 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiEnvImportResultRespDTO {

    private long createdCount;
    private long overwrittenCount;
    private long skippedCount;
}
