package io.github.xiaomisum.robotest.model.dto.request.apitest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回报批次完成请求（POST /api/ci/batches/{batchId}/completion，GitLab 集成详细设计 4.6）。
 * <p>
 * CI 在 after_script 回报（执行失败也须回报）；平台按批次终态去重，重复回报被忽略。
 * status 取值 success / failed（Service 层校验）；actualCases 为 CI 侧
 * {@code __Ryze_Native_Result__} 信封文件数，与平台快照期望数对账。
 */
@Data
public class ApiGitBatchCompletionReqDTO {

    /** 回报状态：success / failed，取值校验在 Service 层执行 */
    @NotBlank(message = "回报状态不能为空")
    @Size(max = 20, message = "回报状态不能超过 20 字符")
    private String status;

    /** CI 侧期望用例数（取自冻结清单），对账留痕 */
    @NotNull(message = "期望用例数不能为空")
    @Min(value = 0, message = "期望用例数不能为负数")
    private Integer expectedCases;

    /** CI 侧实际用例数：信封文件数 */
    @NotNull(message = "实际用例数不能为空")
    @Min(value = 0, message = "实际用例数不能为负数")
    private Integer actualCases;

    /** 流水线链接，失败时报告留痕 */
    @Size(max = 500, message = "流水线链接不能超过 500 字符")
    private String pipelineUrl;
}
