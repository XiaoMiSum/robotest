package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskExecutionCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskCountRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskEventsRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskProposalRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Map;
import java.util.UUID;

/**
 * 统一任务资源（总册 3.6，任务 · 执行 · 提案三资源）：提交（创建即启动）、轮询、
 * 终止 / 再次执行、轨迹查询与提案确认。
 */
public interface AiTaskService {

    /**
     * 3.6.2 提交（创建即启动执行；waitSeconds ∈ [0,10] 同步等待，超时返回执行中状态转轮询）。
     * projectId / workspaceId / userId 为请求头解析后的上下文（C4），置于入参前段使 @AuditLog
     * 可取首个 UUID 作 entityId（切面只摘要简单类型，创建类动作按先例记归属作用域）。
     */
    AiTaskRespDTO submit(AiTaskSubmitReqDTO reqDTO, UUID activeProjectId, UUID activeWorkspaceId, UUID userId,
            LoginUser loginUser);

    /**
     * 内部提交（系统触发：需求导入 / 助手解析等，详设 4.4 提交失败只记日志由调用方负责）。
     * 无登录态，不做权限校验，总开关与类型 / 输入 / 预算 / 模型校验照常。
     */
    AiTaskRespDTO submitInternal(String type, Map<String, Object> input, UUID submittedBy, UUID projectId,
            UUID workspaceId);

    /** 3.6.3 列表：带 X-Active-Project 头按项目过滤，未附带则返回本人提交的任务 */
    PageResult<AiTaskRespDTO> page(AiTaskPageReqDTO pageReq, UUID activeProjectId, UUID userId);

    /** 3.6.3 进行中计数（顶栏指示器）：status = running，过滤口径与列表一致 */
    AiTaskCountRespDTO countActive(UUID activeProjectId, UUID userId, LoginUser loginUser);

    /** 3.6.3 详情：最新一次执行 + 提案清单摘要（最新一次执行）+ documentMeta */
    AiTaskRespDTO getDetail(UUID taskId, LoginUser loginUser);

    /** 3.6.3 提案详情：完整内容（审核预览），键在最新一次执行的提案中定位 */
    AiTaskProposalRespDTO getProposal(UUID taskId, String proposalKey, LoginUser loginUser);

    /** 3.6.4 轨迹查询：afterSeq 游标增量（默认 200 条，上限 500），nextSeq 供续拉 */
    AiTaskEventsRespDTO getEvents(UUID taskId, UUID executionId, Integer afterSeq, Integer pageSize,
            LoginUser loginUser);

    /** 3.6.4 终止：仅 running / waiting_confirmation 的执行可终止（否则 1000018127），任务置 aborted */
    AiTaskRespDTO abort(UUID taskId, UUID executionId, LoginUser loginUser);

    /** 3.6.4 再次执行：任务 aborted / proposals_ready 时可发起，新建执行实例并复用原执行模型 */
    AiTaskRespDTO reExecute(UUID taskId, AiTaskExecutionCreateReqDTO reqDTO, LoginUser loginUser);

    /** 3.6.5 提案确认：批量逐项、逐项事务，整体 200、逐项成败回执 */
    AiTaskConfirmRespDTO confirm(UUID taskId, AiTaskConfirmReqDTO reqDTO, LoginUser loginUser);

    /**
     * 任务是否进行中（status = running，含执行挂起）：跨层查询的唯一入口，
     * 供需求导入等域改经任务服务判定，禁止直读 ai_task（迁移计划层 A）。
     */
    boolean isTaskRunning(UUID taskId);
}
