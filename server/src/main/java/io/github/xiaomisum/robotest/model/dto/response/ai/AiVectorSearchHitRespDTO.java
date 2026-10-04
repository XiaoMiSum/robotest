package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 向量检索命中（详设 4.4 读侧）：distance 仅作排序信号，
 * 口径随 operator 而变（cosine/l2 为距离、inner_product 为负内积），不对外承诺阈值（05 决策）。
 */
@Data
public class AiVectorSearchHitRespDTO {

    private UUID projectId;

    private String entityType;

    private UUID entityId;

    private Integer chunkIndex;

    /** 命中原文分块，直接回显上下文（详设 2.6） */
    private String content;

    private Double distance;
}
