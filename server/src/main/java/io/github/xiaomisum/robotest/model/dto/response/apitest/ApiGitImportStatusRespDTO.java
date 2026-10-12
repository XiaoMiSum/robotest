package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

import java.util.Map;

/**
 * 导入进度响应（GitLab 集成详细设计 3.2）：前端轮询用，进度不落库。
 */
@Data
public class ApiGitImportStatusRespDTO {

    /** idle / fetch（拉取源码）/ scan（扫描测试类）/ parse（解析用例）/ merge（合并环境）/ persist（回写结果）/ done / failed */
    private String stage;
    /** 已处理数（fetch、scan 阶段为文件数，parse 及之后为用例 / 场景数） */
    private Integer processed;
    private Integer total;
    /** failed 时的失败原因，其余阶段为空 */
    private String error;
    /** done 时的结果汇总，与仓库配置 last_import_summary 同构 */
    private Map<String, Object> summary;
}
