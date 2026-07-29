package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestCaseModuleConvertMapper;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.project.TestCaseModuleService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestCaseModuleServiceImpl implements TestCaseModuleService {

    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    @Override
    public List<TestCaseModuleTreeRespDTO> getModuleTree(UUID projectId) {
        List<TestCaseModule> modules = testCaseModuleMapper.listByProjectId(projectId);

        List<TestCaseModuleTreeRespDTO> dtos = modules.stream()
                .map(this::convertToTreeDTO)
                .collect(Collectors.toList());

        return buildTree(dtos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseModuleTreeRespDTO createModule(UUID projectId, TestCaseModuleCreateReqDTO reqDTO) {
        if (!Constants.ModuleType.DIRECTORY.equals(reqDTO.getType())
                && !Constants.ModuleType.DOCUMENT.equals(reqDTO.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        if (reqDTO.getParentId() != null) {
            TestCaseModule parent = testCaseModuleMapper.selectById(reqDTO.getParentId());
            if (parent == null || !parent.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
            }
        }

        TestCaseModule existing = testCaseModuleMapper.findByNameAndParent(
                projectId, reqDTO.getParentId(), reqDTO.getName());
        if (existing != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NAME_EXISTS);
        }

        TestCaseModule module = new TestCaseModule();
        module.setProjectId(projectId);
        module.setParentId(reqDTO.getParentId());
        module.setType(reqDTO.getType());
        module.setName(reqDTO.getName());
        module.setSortOrder(0);
        testCaseModuleMapper.insert(module);

        if (Constants.ModuleType.DOCUMENT.equals(reqDTO.getType())) {
            TestCaseNode rootNode = new TestCaseNode();
            rootNode.setDocumentId(module.getId());
            rootNode.setParentId(null);
            rootNode.setType(Constants.NodeType.NORMAL);
            rootNode.setTitle(reqDTO.getName());
            rootNode.setSortOrder(0);
            rootNode.setVersion(1);
            testCaseNodeMapper.insert(rootNode);
        }

        return convertToTreeDTO(module);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseModuleTreeRespDTO updateModule(UUID moduleId, TestCaseModuleUpdateReqDTO reqDTO) {
        TestCaseModule module = testCaseModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
        }

        boolean moved = reqDTO.getTargetIndex() != null;
        if (moved) {
            moveModule(module, reqDTO.getParentId(), reqDTO.getTargetIndex());
        }

        if (reqDTO.getName() != null) {
            TestCaseModule existing = testCaseModuleMapper.findByNameAndParentExcludingId(
                    module.getProjectId(), module.getParentId(), reqDTO.getName(), moduleId);
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NAME_EXISTS);
            }
            module.setName(reqDTO.getName());
        }

        // 仅落库本次变更字段；移动到根层级时 parentId 为 null，updateById 会忽略，须用 default 方法显式 set
        if (moved) {
            testCaseModuleMapper.updateParentAndOrder(moduleId, module.getParentId(), module.getSortOrder());
        }
        if (reqDTO.getName() != null) {
            testCaseModuleMapper.updateName(moduleId, reqDTO.getName());
        }
        return convertToTreeDTO(module);
    }

    // 移动：校验目标父合法性（存在、同项目、目录类型、无循环引用）后，重排目标层级的 sortOrder；
    // 存量数据 sortOrder 全为 0，首次移动时一并按当前展示顺序回写修复
    private void moveModule(TestCaseModule module, UUID targetParentId, int targetIndex) {
        if (targetParentId != null) {
            TestCaseModule parent = testCaseModuleMapper.selectById(targetParentId);
            if (parent == null || !parent.getProjectId().equals(module.getProjectId())
                    || !Constants.ModuleType.DIRECTORY.equals(parent.getType())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_MOVE_TARGET_INVALID);
            }

            // 沿 parentId 向上回溯，禁止移到自身或其子孙下形成循环
            UUID cursor = targetParentId;
            while (cursor != null) {
                if (cursor.equals(module.getId())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_MOVE_CYCLE);
                }
                TestCaseModule ancestor = testCaseModuleMapper.selectById(cursor);
                cursor = ancestor == null ? null : ancestor.getParentId();
            }
        }

        if (testCaseModuleMapper.findByNameExcludingId(
                module.getProjectId(), targetParentId, module.getName(), module.getId()) != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NAME_EXISTS);
        }

        List<TestCaseModule> siblings = testCaseModuleMapper.findSiblingsByParent(
                module.getProjectId(), targetParentId, module.getId());

        module.setParentId(targetParentId);
        siblings.add(Math.clamp(targetIndex, 0, siblings.size()), module);
        for (int i = 0; i < siblings.size(); i++) {
            TestCaseModule sibling = siblings.get(i);
            if (sibling.getSortOrder() == i) {
                continue;
            }
            sibling.setSortOrder(i);
            if (!sibling.getId().equals(module.getId())) {
                testCaseModuleMapper.updateSortOrder(sibling.getId(), i);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(UUID moduleId) {
        TestCaseModule module = testCaseModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_FOUND);
        }

        if (Constants.ModuleType.DIRECTORY.equals(module.getType())) {
            Long childCount = testCaseModuleMapper.countByParentId(moduleId);
            if (childCount > 0) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_MODULE_NOT_EMPTY);
            }
        }

        if (Constants.ModuleType.DOCUMENT.equals(module.getType())) {
            testCaseNodeMapper.deleteByDocumentId(moduleId);
        }

        testCaseModuleMapper.deleteById(moduleId);
    }

    private List<TestCaseModuleTreeRespDTO> buildTree(List<TestCaseModuleTreeRespDTO> nodes) {
        Map<String, List<TestCaseModuleTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<TestCaseModuleTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots;
    }

    private void fillChildren(TestCaseModuleTreeRespDTO node,
            Map<String, List<TestCaseModuleTreeRespDTO>> parentMap) {
        List<TestCaseModuleTreeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private TestCaseModuleTreeRespDTO convertToTreeDTO(TestCaseModule module) {
        return TestCaseModuleConvertMapper.INSTANCE.toTreeDTO(module);
    }
}
