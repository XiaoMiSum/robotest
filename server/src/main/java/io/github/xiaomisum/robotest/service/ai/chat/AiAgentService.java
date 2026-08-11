package io.github.xiaomisum.robotest.service.ai.chat;

import io.github.xiaomisum.robotest.model.dto.request.ai.AiAgentSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAgentRespDTO;

import java.util.List;
import java.util.UUID;

public interface AiAgentService {

    /**
     * 全部模板位清单（customized 表示存在数据库记录，初始化种子落库后均为 true）
     */
    List<AiAgentRespDTO> getAgents();

    AiAgentDetailRespDTO getAgentDetail(String functionType);

    /**
     * 保存自定义模板：格式约束段仅 formatEditable=true 时接受修改；
     * 模板记录由初始化脚本全量落库，不存在"新建模板位"，未命中视为配置缺失
     */
    void saveAgent(String functionType, AiAgentSaveReqDTO reqDTO, UUID userId);

    /**
     * 恢复默认：将该模板位的数据库记录重置为内置默认内容（JSON 仅作恢复数据源）；
     * 记录不存在时按默认内容重建，保证数据库始终有记录
     */
    void restoreDefault(String functionType, UUID userId);
}
