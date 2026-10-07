package io.github.xiaomisum.robotest.model.dto.request.ai;

import lombok.Data;

import java.util.UUID;

/**
 * 页面实体引用（详设 3.3 context / 3.5 attachments）：数据引用而非 API 上下文（C4），
 * 类型取追溯节点类型白名单；entityId 是否必填由各使用点服务层校验（上下文可省略，附件必填）。
 */
@Data
public class AiAssistantEntityRefReqDTO {

    /** 实体类型：requirement / module / mindmap_document / test_case / test_review / test_plan */
    private String entityType;

    /** 实体标识 */
    private UUID entityId;

    /** 实体标题（展示用，不参与校验） */
    private String entityTitle;
}
