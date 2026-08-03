package io.github.xiaomisum.robotest.model.dto.response.ai;

import lombok.Data;

import java.util.List;

/**
 * 助手会话列表响应（键集分页，nextCursor 为空表示无更多，详细设计 3.1）
 */
@Data
public class AiConversationListRespDTO {

    private List<AiConversationItemRespDTO> items;
    private String nextCursor;
}
