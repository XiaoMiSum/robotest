package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 任务响应（详设 3.6.2 提交 / 3.6.3 列表与详情）：列表项为 latestExecution 摘要，
 * 详情另附 documentMeta 与最新一次执行的提案清单摘要；progress / phase / result 等
 * 旧字段随 S3 任务表重建移除。
 */
@Data
public class AiTaskRespDTO {

    private UUID taskId;

    /** 提交 / 再次执行响应携带本次执行 ID，其余场景为 null */
    private UUID executionId;

    private String type;

    /** running / proposals_ready / closed / aborted */
    private String status;

    /** 提交 / 再次执行响应携带本次签发的预算快照，其余场景为 null */
    private Map<String, Object> budget;

    /** 列表与详情：最新一次执行（3.6.3），提交响应为 null（由 executionId / budget 字段承载） */
    private AiTaskExecutionRespDTO latestExecution;

    private UUID submittedBy;

    /** 任务级 token 汇总（跨全部执行累加） */
    private Integer tokensIn;

    private Integer tokensOut;

    private java.time.LocalDateTime createdAt;

    /** 任务级终止原因（aborted 时携带），其余状态为 null */
    private AiTerminateReasonRespDTO terminateReason;

    /** 文档级元数据原始值（result_meta），确认面板预填数据源 */
    private Map<String, Object> resultMeta;

    /** result_meta 解析出的文档级识别结果（3.6.3 documentMeta），无为 null */
    private DocumentMeta documentMeta;

    /** 详情 / 同步等待完成：最新一次执行的提案清单摘要（3.6.3），其余为 null */
    private List<AiTaskProposalSummaryRespDTO> proposals;

    @Data
    public static class DocumentMeta {

        /** AI 从文档识别的被测业务系统版本，识别不到为 null */
        private String detectedVersion;

        /** 识别依据的原文引语，无为 null */
        private String versionEvidence;
    }
}
