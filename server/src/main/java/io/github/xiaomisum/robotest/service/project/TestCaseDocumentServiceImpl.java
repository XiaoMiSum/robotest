package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.convert.TestCaseDocumentConvertMapper;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TestCaseDocumentServiceImpl implements TestCaseDocumentService {

    @Resource
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public List<TestCaseDocumentRespDTO> getTestCaseList(UUID projectId, UUID userId, UUID moduleId) {
        projectAccessGuard.requireProjectMember(projectId, userId);

        List<TestCaseDocument> documents;
        if (moduleId != null) {
            documents = testCaseDocumentMapper.listByModuleId(moduleId);
        } else {
            documents = testCaseDocumentMapper.listByProjectId(projectId);
        }

        return documents.stream()
                .map(TestCaseDocumentConvertMapper.INSTANCE::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDocumentRespDTO createTestCase(UUID projectId, UUID userId, TestCaseDocumentCreateReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, userId);

        if (reqDTO.getModuleId() != null) {
            ProjectModule parent = projectModuleMapper.selectById(reqDTO.getModuleId());
            if (parent == null || !parent.getProjectId().equals(projectId)) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND);
            }
        }

        // 同模块下用例名称唯一
        TestCaseDocument existing = testCaseDocumentMapper.findByNameAndModule(
                projectId, reqDTO.getModuleId(), reqDTO.getName());
        if (existing != null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NAME_EXISTS);
        }

        TestCaseDocument document = new TestCaseDocument();
        document.setProjectId(projectId);
        document.setModuleId(reqDTO.getModuleId());
        document.setName(reqDTO.getName());
        document.setSortOrder(0);
        document.setLayout(Map.of("template", "right", "offsets", Map.of()));
        testCaseDocumentMapper.insert(document);

        // 自动创建根节点（title = name，type = normal，version = 1）
        TestCaseNode rootNode = new TestCaseNode();
        rootNode.setDocumentId(document.getId());
        rootNode.setParentId(null);
        rootNode.setType(Constants.NodeType.NORMAL);
        rootNode.setTitle(reqDTO.getName());
        rootNode.setSortOrder(0);
        rootNode.setVersion(1);
        testCaseNodeMapper.insert(rootNode);

        return TestCaseDocumentConvertMapper.INSTANCE.toRespDTO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDocumentRespDTO updateTestCase(UUID documentId, UUID userId, TestCaseDocumentUpdateReqDTO reqDTO) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);

        boolean moved = reqDTO.getTargetIndex() != null;
        if (moved) {
            moveDocument(document, reqDTO.getModuleId(), reqDTO.getTargetIndex());
        }

        if (reqDTO.getName() != null) {
            TestCaseDocument existing = testCaseDocumentMapper.findByNameAndModuleExcludingId(
                    document.getProjectId(), document.getModuleId(), reqDTO.getName(), documentId);
            if (existing != null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NAME_EXISTS);
            }
        }

        if (moved) {
            testCaseDocumentMapper.updateModuleAndOrder(documentId, document.getModuleId(), document.getSortOrder());
        }
        if (reqDTO.getName() != null) {
            testCaseDocumentMapper.updateName(documentId, reqDTO.getName());
        }
        if (reqDTO.getLayout() != null) {
            TestCaseDocument layoutUpdate = new TestCaseDocument();
            layoutUpdate.setId(documentId);
            layoutUpdate.setLayout(reqDTO.getLayout());
            testCaseDocumentMapper.updateById(layoutUpdate);
        }
        return TestCaseDocumentConvertMapper.INSTANCE.toRespDTO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTestCase(UUID documentId, UUID userId) {
        TestCaseDocument document = testCaseDocumentMapper.selectById(documentId);
        if (document == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        projectAccessGuard.requireProjectMember(document.getProjectId(), userId);

        // 级联删除节点
        testCaseNodeMapper.deleteByDocumentId(documentId);
        // 删除文档
        testCaseDocumentMapper.deleteById(documentId);
    }

    /**
     * 移动文档到目标模块的指定位置，重排目标层级 sortOrder。
     */
    private void moveDocument(TestCaseDocument document, UUID targetModuleId, int targetIndex) {
        if (targetModuleId != null) {
            ProjectModule targetModule = projectModuleMapper.selectById(targetModuleId);
            if (targetModule == null || !targetModule.getProjectId().equals(document.getProjectId())) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_MODULE_NOT_FOUND);
            }
        }

        List<TestCaseDocument> siblings = testCaseDocumentMapper.findSiblingsByModule(
                document.getProjectId(), targetModuleId, document.getId());

        document.setModuleId(targetModuleId);
        siblings.add(Math.clamp(targetIndex, 0, siblings.size()), document);
        for (int i = 0; i < siblings.size(); i++) {
            TestCaseDocument sibling = siblings.get(i);
            if (sibling.getSortOrder() == i) {
                continue;
            }
            sibling.setSortOrder(i);
            if (!sibling.getId().equals(document.getId())) {
                testCaseDocumentMapper.updateSortOrder(sibling.getId(), i);
            }
        }
    }
}
