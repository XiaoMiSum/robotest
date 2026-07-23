package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import xyz.migoo.framework.common.pojo.PageResult;

public interface TestCaseNodeService {

    TestCaseDocumentNodesRespDTO getDocumentNodes(String documentId);

    TestCaseNodeTreeRespDTO getCaseDetail(String caseId);

    /**
     * 查询项目下的用例列表（支持按标题关键词、优先级过滤）
     *
     * @param projectId 项目 ID
     * @param keyword   标题关键词（模糊搜索）
     * @param priority  优先级筛选
     * @param pageNo    页码
     * @param pageSize  每页大小
     * @return 分页用例列表
     */
    PageResult<TestCaseCaseListRespDTO> getCaseList(String projectId, String keyword,
                                                     String priority, Integer pageNo, Integer pageSize);

    /**
     * 更新用例节点属性（标题、优先级）
     *
     * @param caseId 用例节点 ID
     * @param reqDTO 更新内容
     */
    void updateCaseNode(String caseId, TestCaseNodeUpdateReqDTO reqDTO);
}
