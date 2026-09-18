package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugStatusChangeReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;

import java.util.UUID;

/**
 * 缺陷命令面端口（05 §3.1.3 CQRS 落位）：写操作（创建/编辑/状态流转/确认/指派）。
 * 读模型见 {@link BugQueryService}。
 */
public interface BugService {

    String createBug(UUID projectId, UUID userId, BugCreateReqDTO reqDTO);

    void updateBug(UUID bugId, UUID userId, BugUpdateReqDTO reqDTO);

    /**
     * 变更缺陷状态（四态状态机：active → resolved/rejected → closed，重开回 active，裁决见 BugWorkflow）
     *
     * @param bugId  缺陷 ID
     * @param userId 操作用户 ID
     * @param reqDTO 目标状态、说明、解决方案及重复缺陷指向
     */
    void changeBugStatus(UUID bugId, UUID userId, BugStatusChangeReqDTO reqDTO);

    /**
     * 确认缺陷（仅激活状态且未确认时可执行）
     *
     * @param bugId  缺陷 ID
     * @param userId 操作用户 ID
     */
    void confirmBug(UUID bugId, UUID userId);

    /**
     * 指派缺陷处理人
     *
     * @param bugId      缺陷 ID
     * @param userId     操作用户 ID
     * @param assigneeId 新处理人用户 ID
     */
    void assignBug(UUID bugId, UUID userId, UUID assigneeId);
}
