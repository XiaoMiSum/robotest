package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.ProjectModuleConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
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
public class ProjectModuleServiceImpl implements ProjectModuleService {

    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public List<ProjectModuleTreeRespDTO> getModuleTree(UUID projectId, UUID userId, String assetType) {
        projectAccessGuard.requireProjectMember(projectId, userId);
        List<ProjectModule> modules = projectModuleMapper.listByProjectId(projectId);

        List<ProjectModuleTreeRespDTO> dtos = modules.stream()
                .map(this::convertToTreeDTO)
                .collect(Collectors.toList());

        // 根据资产类型合并对应文档节点到模块树（文档直接挂载到模块树，与旧 test_case_module 一致）
        if ("testcase".equals(assetType)) {
            List<TestCaseDocument> documents = testCaseDocumentMapper.listByProjectId(projectId);
            List<ProjectModuleTreeRespDTO> docDtos = documents.stream()
                    .map(this::convertDocumentToTreeDTO)
                    .toList();
            dtos.addAll(docDtos);
        }

        return buildTree(dtos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectModuleTreeRespDTO createModule(UUID projectId, UUID userId, ProjectModuleCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);

        if (reqDTO.getParentId() != null) {
            ProjectModule parent = projectModuleMapper.selectById(reqDTO.getParentId());
            if (parent == null || !parent.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND);
            }
        }

        ProjectModule existing = projectModuleMapper.findByNameAndParent(
                projectId, reqDTO.getParentId(), reqDTO.getName());
        if (existing != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NAME_EXISTS);
        }

        ProjectModule module = new ProjectModule();
        module.setProjectId(projectId);
        module.setParentId(reqDTO.getParentId());
        module.setName(reqDTO.getName());
        module.setSortOrder(0);
        projectModuleMapper.insert(module);

        return convertToTreeDTO(module);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectModuleTreeRespDTO updateModule(UUID moduleId, UUID userId, ProjectModuleUpdateReqDTO reqDTO) {
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(module.getProjectId(), userId);

        boolean moved = reqDTO.getTargetIndex() != null;
        if (moved) {
            moveModule(module, reqDTO.getParentId(), reqDTO.getTargetIndex());
        }

        if (reqDTO.getName() != null) {
            ProjectModule existing = projectModuleMapper.findByNameAndParentExcludingId(
                    module.getProjectId(), module.getParentId(), reqDTO.getName(), moduleId);
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NAME_EXISTS);
            }
            module.setName(reqDTO.getName());
        }

        if (moved) {
            projectModuleMapper.updateParentAndOrder(moduleId, module.getParentId(), module.getSortOrder());
        }
        if (reqDTO.getName() != null) {
            projectModuleMapper.updateName(moduleId, reqDTO.getName());
        }
        return convertToTreeDTO(module);
    }

    // 移动：校验目标父合法性（存在、同项目）后，重排目标层级的 sortOrder；
    // 存量数据 sortOrder 全为 0，首次移动时一并按当前展示顺序回写修复
    private void moveModule(ProjectModule module, UUID targetParentId, int targetIndex) {
        if (targetParentId != null) {
            ProjectModule parent = projectModuleMapper.selectById(targetParentId);
            if (parent == null || !parent.getProjectId().equals(module.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_MOVE_TARGET_INVALID);
            }

            // 沿 parentId 向上回溯，禁止移到自身或其子孙下形成循环
            UUID cursor = targetParentId;
            while (cursor != null) {
                if (cursor.equals(module.getId())) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_MOVE_CYCLE);
                }
                ProjectModule ancestor = projectModuleMapper.selectById(cursor);
                cursor = ancestor == null ? null : ancestor.getParentId();
            }
        }

        List<ProjectModule> siblings = projectModuleMapper.findSiblingsByParent(
                module.getProjectId(), targetParentId, module.getId());

        module.setParentId(targetParentId);
        siblings.add(Math.clamp(targetIndex, 0, siblings.size()), module);
        for (int i = 0; i < siblings.size(); i++) {
            ProjectModule sibling = siblings.get(i);
            if (sibling.getSortOrder() == i) {
                continue;
            }
            sibling.setSortOrder(i);
            if (!sibling.getId().equals(module.getId())) {
                projectModuleMapper.updateSortOrder(sibling.getId(), i);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(UUID moduleId, UUID userId) {
        ProjectModule module = projectModuleMapper.selectById(moduleId);
        if (module == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(module.getProjectId(), userId);

        // 有子模块则拒绝删除
        Long childCount = projectModuleMapper.countByParentId(moduleId);
        if (childCount > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_EMPTY);
        }

        // 模块下仍有用例文档也拒绝删除：文档 module_id 指向本模块，直接删会孤儿化文档使其从树上消失
        long docCount = testCaseDocumentMapper.countByModuleId(moduleId);
        if (docCount > 0) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_EMPTY);
        }

        projectModuleMapper.deleteById(moduleId);
    }

    private List<ProjectModuleTreeRespDTO> buildTree(List<ProjectModuleTreeRespDTO> nodes) {
        Map<String, List<ProjectModuleTreeRespDTO>> parentMap = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getParentId() != null ? n.getParentId().toString() : Constants.Tree.ROOT_KEY));

        List<ProjectModuleTreeRespDTO> roots = parentMap.getOrDefault(Constants.Tree.ROOT_KEY, new ArrayList<>());
        roots.forEach(root -> fillChildren(root, parentMap));
        return roots;
    }

    private void fillChildren(ProjectModuleTreeRespDTO node,
                              Map<String, List<ProjectModuleTreeRespDTO>> parentMap) {
        List<ProjectModuleTreeRespDTO> children = parentMap.getOrDefault(node.getId().toString(), new ArrayList<>());
        node.setChildren(children);
        children.forEach(child -> fillChildren(child, parentMap));
    }

    private ProjectModuleTreeRespDTO convertToTreeDTO(ProjectModule module) {
        return ProjectModuleConvertMapper.INSTANCE.toTreeDTO(module);
    }

    private ProjectModuleTreeRespDTO convertDocumentToTreeDTO(TestCaseDocument document) {
        ProjectModuleTreeRespDTO dto = new ProjectModuleTreeRespDTO();
        dto.setId(document.getId());
        dto.setParentId(document.getModuleId());
        dto.setType("document");
        dto.setName(document.getName());
        dto.setSortOrder(document.getSortOrder());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setChildren(new ArrayList<>());
        return dto;
    }
}
