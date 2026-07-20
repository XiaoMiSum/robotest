package io.github.xiaomisum.robotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import io.github.xiaomisum.robotest.repository.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.TestCaseModuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestCaseModuleServiceImpl implements TestCaseModuleService {

    private static final String TYPE_DIRECTORY = "directory";
    private static final String TYPE_DOCUMENT = "document";

    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    @Override
    public List<TestCaseModuleTreeRespDTO> getModuleTree(String projectId) {
        List<TestCaseModule> modules = testCaseModuleMapper.selectList(
                new LambdaQueryWrapper<TestCaseModule>()
                        .eq(TestCaseModule::getProjectId, projectId)
                        .orderByAsc(TestCaseModule::getSortOrder));

        List<TestCaseModuleTreeRespDTO> dtos = modules.stream()
                .map(this::convertToTreeDTO)
                .collect(Collectors.toList());

        return buildTree(dtos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseModuleTreeRespDTO createModule(String projectId, TestCaseModuleCreateReqDTO reqDTO) {
        if (!TYPE_DIRECTORY.equals(reqDTO.getType()) && !TYPE_DOCUMENT.equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        if (reqDTO.getParentId() != null) {
            TestCaseModule parent = testCaseModuleMapper.selectById(reqDTO.getParentId());
            if (parent == null || !parent.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
            }
        }

        TestCaseModule existing = testCaseModuleMapper.selectOne(
                new LambdaQueryWrapper<TestCaseModule>()
                        .eq(TestCaseModule::getProjectId, projectId)
                        .eq(TestCaseModule::getParentId, reqDTO.getParentId())
                        .eq(TestCaseModule::getName, reqDTO.getName()));
        if (existing != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NAME_EXISTS);
        }

        TestCaseModule module = new TestCaseModule();
        module.setId(UUID.randomUUID().toString());
        module.setProjectId(projectId);
        module.setParentId(reqDTO.getParentId());
        module.setType(reqDTO.getType());
        module.setName(reqDTO.getName());
        module.setSortOrder(0);
        testCaseModuleMapper.insert(module);

        if (TYPE_DOCUMENT.equals(reqDTO.getType())) {
            TestCaseNode rootNode = new TestCaseNode();
            rootNode.setId(UUID.randomUUID().toString());
            rootNode.setDocumentId(module.getId());
            rootNode.setParentId(null);
            rootNode.setType("normal");
            rootNode.setTitle(reqDTO.getName());
            rootNode.setSortOrder(0);
            rootNode.setVersion(1);
            testCaseNodeMapper.insert(rootNode);
        }

        return convertToTreeDTO(module);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseModuleTreeRespDTO updateModule(String moduleId, TestCaseModuleUpdateReqDTO reqDTO) {
        TestCaseModule module = testCaseModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
        }

        if (reqDTO.getName() != null) {
            TestCaseModule existing = testCaseModuleMapper.selectOne(
                    new LambdaQueryWrapper<TestCaseModule>()
                            .eq(TestCaseModule::getProjectId, module.getProjectId())
                            .eq(TestCaseModule::getParentId, module.getParentId())
                            .eq(TestCaseModule::getName, reqDTO.getName())
                            .ne(TestCaseModule::getId, moduleId));
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NAME_EXISTS);
            }
            module.setName(reqDTO.getName());
        }

        testCaseModuleMapper.updateById(module);
        return convertToTreeDTO(module);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(String moduleId) {
        TestCaseModule module = testCaseModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
        }

        if (TYPE_DIRECTORY.equals(module.getType())) {
            Long childCount = testCaseModuleMapper.selectCount(
                    new LambdaQueryWrapper<TestCaseModule>()
                            .eq(TestCaseModule::getParentId, moduleId));
            if (childCount > 0) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_EMPTY);
            }
        }

        if (TYPE_DOCUMENT.equals(module.getType())) {
            testCaseNodeMapper.delete(
                    new LambdaQueryWrapper<TestCaseNode>()
                            .eq(TestCaseNode::getDocumentId, moduleId));
        }

        testCaseModuleMapper.deleteById(moduleId);
    }

    private List<TestCaseModuleTreeRespDTO> buildTree(List<TestCaseModuleTreeRespDTO> nodes) {
        Map<String, List<TestCaseModuleTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId() : "root"));

        List<TestCaseModuleTreeRespDTO> roots = parentMap.getOrDefault("root", new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots;
    }

    private void fillChildren(TestCaseModuleTreeRespDTO node,
                               Map<String, List<TestCaseModuleTreeRespDTO>> parentMap) {
        List<TestCaseModuleTreeRespDTO> children = parentMap.getOrDefault(node.getId(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private TestCaseModuleTreeRespDTO convertToTreeDTO(TestCaseModule module) {
        TestCaseModuleTreeRespDTO dto = new TestCaseModuleTreeRespDTO();
        dto.setId(module.getId());
        dto.setParentId(module.getParentId());
        dto.setType(module.getType());
        dto.setName(module.getName());
        dto.setSortOrder(module.getSortOrder());
        dto.setCreatedAt(module.getCreatedAt());
        return dto;
    }
}
