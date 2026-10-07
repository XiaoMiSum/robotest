package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TestCaseNodeService {

    TestCaseDocumentNodesRespDTO getDocumentNodes(UUID projectId, UUID documentId, UUID userId);

    TestCaseNodeTreeRespDTO getCaseDetail(UUID projectId, UUID caseId, UUID userId);

    /**
     * 查询项目下的用例列表（支持按标题关键词、优先级过滤）
     *
     * @param projectId 项目 ID
     * @param userId    当前用户 ID（用于项目归属校验）
     * @param keyword   标题关键词（模糊搜索）
     * @param priority  优先级筛选
     * @param pageNo    页码
     * @param pageSize  每页大小
     * @return 分页用例列表
     */
    PageResult<TestCaseCaseListRespDTO> getCaseList(UUID projectId, UUID userId, String keyword,
                                                     String priority, Integer pageNo, Integer pageSize);

    /**
     * 更新用例节点属性（标题、优先级）
     *
     * @param projectId 活动项目 ID（X-Active-Project 头，须与资源归属一致）
     * @param caseId 用例节点 ID
     * @param userId 当前用户 ID（用于项目归属校验）
     * @param reqDTO 更新内容
     */
    void updateCaseNode(UUID projectId, UUID caseId, UUID userId, TestCaseNodeUpdateReqDTO reqDTO);

    /**
     * AI 助手确认执行：新建用例节点并同步重嵌（详设 04-ai-assistant 4.2⑥）
     *
     * @param projectId  活动项目（intent.scope，须与文档归属一致）
     * @param userId     执行人（项目成员校验与向量索引操作者）
     * @param documentId 目标文档；空取项目下首份功能用例文档（sortOrder 升序）
     * @param parentId   父节点；空挂文档根节点（parentId 为 null 的自动根）
     * @param fields     字段集：title（必填）、priority、precondition、steps、expected
     * @return 新建节点 ID（回执 caseId）
     */
    UUID createCase(UUID projectId, UUID userId, UUID documentId, UUID parentId, Map<String, Object> fields);

    /**
     * AI 助手确认执行：按字段白名单更新用例（title / priority / precondition / steps / expected，
     * 越界忽略；type 不在 update_case 白名单），属性子节点按 op 替换或追加后稳定重排并重嵌
     */
    void updateCaseFields(UUID projectId, UUID userId, UUID caseId, List<Map<String, Object>> changes);

    /**
     * AI 助手确认执行：批量标记（priority / type 白名单，取值非法忽略，全被忽略时不落库）
     */
    void tagCase(UUID projectId, UUID userId, UUID caseId, List<Map<String, Object>> changes);
}
