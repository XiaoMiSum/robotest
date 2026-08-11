package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体列表项（全部有模板位的功能类型，customized 表示存在数据库记录）
 */
@Data
public class AiAgentRespDTO {

    private String functionType;
    private String name;
    private Boolean customized;
    private Boolean formatEditable;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
