package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体列表项（代码内置全部模板位，customized 表示存在自定义覆盖）
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
