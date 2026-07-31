package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.UUID;

/**
 * 需求池条目服务（US-AI-004）：项目级常规业务功能，不经 AI 网关。
 */
public interface RequirementService {

    PageResult<RequirementListRespDTO> getPage(UUID projectId, String keyword, Integer pageNo, Integer pageSize);

    RequirementDetailRespDTO getDetail(UUID id, UUID projectId);

    String create(UUID projectId, UUID userId, RequirementCreateReqDTO reqDTO);

    /** 编辑：仅创建人或项目管理权限成员 */
    void update(UUID id, UUID projectId, UUID userId, RequirementUpdateReqDTO reqDTO);

    /** 删除：仅创建人或项目管理权限成员（逻辑删除，不影响已生成的用例） */
    void delete(UUID id, UUID projectId, UUID userId);
}
