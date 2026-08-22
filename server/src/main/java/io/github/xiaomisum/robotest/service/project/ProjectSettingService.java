package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.project.ProjectSettingUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.project.ProjectSettingListRespDTO;

import java.util.UUID;

public interface ProjectSettingService {

    /**
     * 查询当前项目指定域（含 common）的设置项，未落库键返回注册表默认值。
     *
     * <p>workspaceId 为 X-Active-Workspace 头上下文，用于项目归属一致性校验。</p>
     */
    ProjectSettingListRespDTO getSettings(UUID projectId, UUID workspaceId, UUID userId, String domain);

    /**
     * 批量更新设置项（逐键 upsert），任一键不在白名单或值非法则整批拒绝。
     *
     * @param workspaceId X-Active-Workspace 头上下文，用于项目归属一致性校验
     * @return 成功写入的键数量
     */
    int updateSettings(UUID projectId, UUID workspaceId, UUID userId, ProjectSettingUpdateReqDTO reqDTO);
}
