package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.UUID;

/**
 * 需求池条目服务（US-AI-004）：项目级常规业务功能，不经 AI 网关。
 */
public interface RequirementService {

    PageResult<RequirementListRespDTO> getPage(UUID projectId, String keyword, String status, Integer pageNo,
            Integer pageSize);

    RequirementDetailRespDTO getDetail(UUID id, UUID projectId);

    String create(UUID projectId, UUID userId, RequirementCreateReqDTO reqDTO);

    /** 编辑：仅创建人或项目管理权限成员；已归档条目禁止编辑 */
    void update(UUID id, UUID projectId, UUID userId, RequirementUpdateReqDTO reqDTO);

    /** 删除：仅创建人或项目管理权限成员（逻辑删除，不影响已生成的用例） */
    void delete(UUID id, UUID projectId, UUID userId);

    /** 归档/取消归档：仅创建人或项目管理权限成员，幂等 */
    void archive(UUID id, UUID projectId, UUID userId, boolean archived);

    /** 文档已关联的需求条目摘要（跳过已删条目，3.1.5） */
    List<RequirementSummaryRespDTO> getDocumentRequirements(UUID documentId, UUID projectId);

    /** 全量设置文档关联的需求条目（差量增删，3.1.5） */
    void setDocumentRequirements(UUID documentId, UUID projectId, List<UUID> requirementIds);

    /**
     * 批量获取项目内条目（含 content，供 AI 生成/补全上下文组装）。
     * 任一条目不存在或不属于当前项目时抛 REQUIREMENT_NOT_FOUND（3.2.1 归属校验）。
     */
    List<RequirementPoolItem> requireByIds(UUID projectId, List<UUID> ids);
}
