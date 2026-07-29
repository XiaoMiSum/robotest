package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestCaseNodeConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseNodeUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestCaseNodeServiceImpl implements TestCaseNodeService {

    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private TestCaseDocumentLayoutMapper testCaseDocumentLayoutMapper;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;

    @Override
    public TestCaseDocumentNodesRespDTO getDocumentNodes(UUID documentId) {
        TestCaseModule document = testCaseModuleMapper.selectById(documentId);
        if (document == null || !Constants.ModuleType.DOCUMENT.equals(document.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }

        List<TestCaseNode> nodes = testCaseNodeMapper.listByDocumentId(documentId);

        List<TestCaseNodeTreeRespDTO> dtos = nodes.stream()
                .map(this::convertToNodeDTO)
                .collect(Collectors.toList());

        TestCaseNodeTreeRespDTO rootNode = buildNodeTree(dtos);

        TestCaseDocumentLayout layout = testCaseDocumentLayoutMapper.findByDocumentId(documentId);

        TestCaseDocumentNodesRespDTO result = new TestCaseDocumentNodesRespDTO();
        result.setNode(rootNode);
        result.setLayout(layout != null ? layout.getLayoutJson() : null);
        return result;
    }

    @Override
    public TestCaseNodeTreeRespDTO getCaseDetail(UUID caseId) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        return convertToNodeDTO(node);
    }

    @Override
    public PageResult<TestCaseCaseListRespDTO> getCaseList(UUID projectId, String keyword,
                                                           String priority, Integer pageNo, Integer pageSize) {
        // 查询项目下所有 document 的 ID
        List<TestCaseModule> documents = testCaseModuleMapper.findDocumentModulesByProjectId(projectId);
        List<String> documentIds = documents.stream()
                .map(doc -> doc.getId().toString())
                .collect(Collectors.toList());

        if (documentIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }

        // 查询所有 case 节点，按标题/优先级过滤
        PageResult<TestCaseNode> page = testCaseNodeMapper.findCasePage(
                new PageParam() {{
                    setPageNo(pageNo);
                    setPageSize(pageSize);
                }}, documentIds, keyword, priority);

        // 构建 documentId → documentName 映射
        Map<String, String> docNameMap = documents.stream()
                .collect(Collectors.toMap(doc -> doc.getId().toString(), TestCaseModule::getName));

        List<TestCaseCaseListRespDTO> dtos = page.getList().stream().map(node -> {
            TestCaseCaseListRespDTO dto = new TestCaseCaseListRespDTO();
            dto.setId(node.getId());
            dto.setTitle(node.getTitle());
            dto.setType(node.getType());
            dto.setPriority(node.getPriority());
            dto.setDocumentId(node.getDocumentId());
            dto.setDocumentName(docNameMap.get(node.getDocumentId().toString()));
            dto.setSortOrder(node.getSortOrder());
            dto.setVersion(node.getVersion());
            dto.setCreatedAt(node.getCreatedAt());
            dto.setUpdatedAt(node.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(dtos, page.getTotal());
    }

    @Override
    public void updateCaseNode(UUID caseId, TestCaseNodeUpdateReqDTO reqDTO) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        if (!Constants.NodeType.CASE.equals(node.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        // 更新载体只携带前端传入的字段，避免全列覆盖导致并发丢失更新
        TestCaseNode update = new TestCaseNode();
        update.setId(caseId);
        if (StringUtils.hasText(reqDTO.getTitle())) {
            update.setTitle(reqDTO.getTitle());
        }
        if (StringUtils.hasText(reqDTO.getPriority())) {
            update.setPriority(reqDTO.getPriority());
        }
        testCaseNodeMapper.updateById(update);
    }

    private TestCaseNodeTreeRespDTO buildNodeTree(List<TestCaseNodeTreeRespDTO> nodes) {
        Map<String, List<TestCaseNodeTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<TestCaseNodeTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots.isEmpty() ? null : roots.getFirst();
    }

    private void fillChildren(TestCaseNodeTreeRespDTO node,
                              Map<String, List<TestCaseNodeTreeRespDTO>> parentMap) {
        List<TestCaseNodeTreeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private TestCaseNodeTreeRespDTO convertToNodeDTO(TestCaseNode node) {
        return TestCaseNodeConvertMapper.INSTANCE.toTreeDTO(node);
    }
}
