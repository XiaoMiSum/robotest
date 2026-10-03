package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskConfirmReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTaskSubmitReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskConfirmRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.Map;
import java.util.UUID;

/**
 * 统一任务资源（详设 3.6）：提交、轮询、取消、重试与产物确认。
 */
public interface AiTaskService {

    /**
     * 3.6.2 提交（waitSeconds ∈ [0,10] 同步等待，超时返回进行中状态转轮询）。
     * projectId / workspaceId / userId 为请求头解析后的上下文（C4），置于入参前段使 @AuditLog
     * 可取首个 UUID 作 entityId（切面只摘要简单类型，创建类动作按先例记归属作用域）。
     */
    AiTaskRespDTO submit(AiTaskSubmitReqDTO reqDTO, UUID activeProjectId, UUID activeWorkspaceId, UUID userId,
            LoginUser loginUser);

    /**
     * 内部提交（系统触发：影响分析等，详设 4.4 提交失败只记日志由调用方负责）。
     * 无登录态，不做权限校验，总开关与类型 / 输入 / 模型校验照常。
     */
    AiTaskRespDTO submitInternal(String type, Map<String, Object> input, UUID submittedBy, UUID projectId,
            UUID workspaceId);

    /** 3.6.3 列表：带 X-Active-Project 头按项目过滤，未附带则返回本人提交的任务 */
    PageResult<AiTaskRespDTO> page(AiTaskPageReqDTO pageReq, UUID activeProjectId, UUID userId);

    /** 3.6.3 详情：succeeded 时附产物清单摘要（含确认状态） */
    AiTaskRespDTO getDetail(UUID taskId, LoginUser loginUser);

    /** 3.6.3 单产物完整内容（审核预览） */
    Map<String, Object> getArtifact(UUID taskId, String artifactKey, LoginUser loginUser);

    /** 3.6.4 取消：pending / running → cancelled，条件更新与执行线程竞争 */
    AiTaskRespDTO cancel(UUID taskId, LoginUser loginUser);

    /** 3.6.4 重试：新建任务复制 input、复用原 model_id，retry_of_task_id 指向原任务（原任务保留） */
    AiTaskRespDTO retry(UUID taskId, LoginUser loginUser);

    /** 3.6.5 产物确认：批量逐项，整体 200、逐项成败回执 */
    AiTaskConfirmRespDTO confirm(UUID taskId, AiTaskConfirmReqDTO reqDTO, LoginUser loginUser);
}
