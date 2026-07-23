package io.github.xiaomisum.robotest.service.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestCaseNodeConvertMapper;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseDocumentNodesRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseDocumentLayout;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.TestCaseDocumentLayoutMapper;
import io.github.xiaomisum.robotest.repository.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.project.TestCaseNodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

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
    public TestCaseDocumentNodesRespDTO getDocumentNodes(String documentId) {
        TestCaseModule document = testCaseModuleMapper.selectById(documentId);
        if (document == null || !Constants.ModuleType.DOCUMENT.equals(document.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }

        List<TestCaseNode> nodes = testCaseNodeMapper.selectList(
                new LambdaQueryWrapper<TestCaseNode>()
                        .eq(TestCaseNode::getDocumentId, documentId)
                        .orderByAsc(TestCaseNode::getSortOrder));

        List<TestCaseNodeTreeRespDTO> dtos = nodes.stream()
                .map(this::convertToNodeDTO)
                .collect(Collectors.toList());

        TestCaseNodeTreeRespDTO rootNode = buildNodeTree(dtos);

        TestCaseDocumentLayout layout = testCaseDocumentLayoutMapper.selectOne(
                new LambdaQueryWrapper<TestCaseDocumentLayout>()
                        .eq(TestCaseDocumentLayout::getDocumentId, documentId));

        TestCaseDocumentNodesRespDTO result = new TestCaseDocumentNodesRespDTO();
        result.setNode(rootNode);
        result.setLayout(layout != null && layout.getLayoutJson() != null
                ? layout.getLayoutJson().toString() : null);
        return result;
    }

    @Override
    public TestCaseNodeTreeRespDTO getCaseDetail(String caseId) {
        TestCaseNode node = testCaseNodeMapper.selectById(caseId);
        if (node == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND);
        }
        return convertToNodeDTO(node);
    }

    private TestCaseNodeTreeRespDTO buildNodeTree(List<TestCaseNodeTreeRespDTO> nodes) {
        Map<String, List<TestCaseNodeTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<TestCaseNodeTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots.isEmpty() ? null : roots.get(0);
    }

    private void fillChildren(TestCaseNodeTreeRespDTO node,
                               Map<String, List<TestCaseNodeTreeRespDTO>> parentMap) {
        List<TestCaseNodeTreeRespDTO> children = parentMap.getOrDefault(node.getId(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private TestCaseNodeTreeRespDTO convertToNodeDTO(TestCaseNode node) {
        return TestCaseNodeConvertMapper.INSTANCE.toTreeDTO(node);
    }
}
