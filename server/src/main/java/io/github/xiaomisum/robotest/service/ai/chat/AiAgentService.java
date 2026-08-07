package io.github.xiaomisum.robotest.service.ai.chat;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiAgentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentRespDTO;

import java.util.List;
import java.util.UUID;

public interface AiAgentService {

    /**
     * 全部模板位清单（代码内置，customized 表示存在自定义覆盖记录）
     */
    List<AiAgentRespDTO> getAgents();

    AiAgentDetailRespDTO getAgentDetail(String functionType);

    /**
     * 保存自定义模板：格式约束段仅 formatEditable=true 时接受修改
     */
    void saveAgent(String functionType, AiAgentSaveReqDTO reqDTO, UUID userId);

    /**
     * 恢复默认：逻辑删除覆盖记录，无记录时幂等成功
     */
    void restoreDefault(String functionType);
}
