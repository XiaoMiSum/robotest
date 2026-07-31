package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.DocumentRequirementRel;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementPoolItem;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.requirement.DocumentRequirementRelMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementPoolItemMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();

    @Mock
    private RequirementPoolItemMapper requirementMapper;
    @Mock
    private DocumentRequirementRelMapper documentRequirementRelMapper;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private AiConfigService aiConfigService;

    @InjectMocks
    private RequirementServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(aiConfigService.getIntSetting("requirementContentMaxLength")).thenReturn(20000);
    }

    private RequirementPoolItem item(UUID projectId, UUID createdBy) {
        RequirementPoolItem item = new RequirementPoolItem();
        item.setId(ITEM_ID);
        item.setProjectId(projectId);
        item.setTitle("登录改版需求");
        item.setContent("需求正文");
        item.setCreatedBy(createdBy);
        item.setUpdatedBy(createdBy);
        return item;
    }

    private RequirementCreateReqDTO createReq(String content) {
        RequirementCreateReqDTO dto = new RequirementCreateReqDTO();
        dto.setTitle("登录改版需求");
        dto.setContent(content);
        return dto;
    }

    private void stubMember(UUID userId, UUID roleId) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(WORKSPACE_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        WorkspaceUser wu = new WorkspaceUser();
        wu.setWorkspaceRole(roleId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, userId)).thenReturn(wu);
    }

    @Test
    void create_contentOverLimit_throwsValidationFailed() {
        when(aiConfigService.getIntSetting("requirementContentMaxLength")).thenReturn(100);
        assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, CREATOR_ID, createReq("x".repeat(101))));
    }

    @Test
    void create_setsCreatorAndUpdater() {
        // insert 时回填主键（模拟 MyBatis-Plus ASSIGN_UUID）
        when(requirementMapper.insert(any(RequirementPoolItem.class))).thenAnswer(inv -> {
            inv.getArgument(0, RequirementPoolItem.class).setId(ITEM_ID);
            return 1;
        });
        service.create(PROJECT_ID, CREATOR_ID, createReq("正文"));
        ArgumentCaptor<RequirementPoolItem> captor = ArgumentCaptor.forClass(RequirementPoolItem.class);
        verify(requirementMapper).insert(captor.capture());
        RequirementPoolItem saved = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(PROJECT_ID, saved.getProjectId());
        org.junit.jupiter.api.Assertions.assertEquals(CREATOR_ID, saved.getCreatedBy());
        org.junit.jupiter.api.Assertions.assertEquals(CREATOR_ID, saved.getUpdatedBy());
    }

    @Test
    void getDetail_crossProject_throwsNotFound() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(UUID.randomUUID(), CREATOR_ID));
        assertThrows(ServiceException.class, () -> service.getDetail(ITEM_ID, PROJECT_ID));
    }

    @Test
    void update_missingItem_throwsNotFound() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(null);
        assertThrows(ServiceException.class,
                () -> service.update(ITEM_ID, PROJECT_ID, CREATOR_ID, new RequirementUpdateReqDTO()));
    }

    @Test
    void update_byCreator_succeeds() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("新标题");
        service.update(ITEM_ID, PROJECT_ID, CREATOR_ID, req);
        verify(requirementMapper).updateById(any(RequirementPoolItem.class));
    }

    @Test
    void update_byWorkspaceAdmin_succeeds() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        stubMember(OTHER_ID, Constants.WorkspaceRole.ADMIN_ID);
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("管理员改标题");
        service.update(ITEM_ID, PROJECT_ID, OTHER_ID, req);
        verify(requirementMapper).updateById(any(RequirementPoolItem.class));
    }

    @Test
    void update_byNonCreatorNonAdmin_throwsNoPermission() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        stubMember(OTHER_ID, Constants.WorkspaceRole.MEMBER_ID);
        assertThrows(ServiceException.class,
                () -> service.update(ITEM_ID, PROJECT_ID, OTHER_ID, new RequirementUpdateReqDTO()));
    }

    @Test
    void delete_byCreator_logicalDeletes() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        service.delete(ITEM_ID, PROJECT_ID, CREATOR_ID);
        verify(requirementMapper).deleteById(ITEM_ID);
    }

    @Test
    void delete_byNonCreatorNonAdmin_throwsNoPermission() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        stubMember(OTHER_ID, Constants.WorkspaceRole.MEMBER_ID);
        assertThrows(ServiceException.class, () -> service.delete(ITEM_ID, PROJECT_ID, OTHER_ID));
    }

    // ==================== 文档关联（3.1.5） ====================

    private TestCaseModule document(UUID projectId) {
        TestCaseModule module = new TestCaseModule();
        module.setId(DOC_ID);
        module.setProjectId(projectId);
        module.setType(Constants.ModuleType.DOCUMENT);
        return module;
    }

    private DocumentRequirementRel rel(UUID requirementId) {
        DocumentRequirementRel r = new DocumentRequirementRel();
        r.setDocumentId(DOC_ID);
        r.setRequirementId(requirementId);
        return r;
    }

    @Test
    void getDocumentRequirements_crossProjectDoc_throwsDocNotFound() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(UUID.randomUUID()));
        assertThrows(ServiceException.class, () -> service.getDocumentRequirements(DOC_ID, PROJECT_ID));
    }

    @Test
    void getDocumentRequirements_skipsDeletedItems() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID reqA = UUID.randomUUID();
        UUID reqB = UUID.randomUUID();
        when(documentRequirementRelMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(rel(reqA), rel(reqB)));
        // reqB 已被逻辑删除：selectBatchIds 只返回 reqA
        RequirementPoolItem a = item(PROJECT_ID, CREATOR_ID);
        a.setId(reqA);
        a.setTitle("条目A");
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(a));

        var result = service.getDocumentRequirements(DOC_ID, PROJECT_ID);
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size());
        org.junit.jupiter.api.Assertions.assertEquals(reqA, result.get(0).getId());
    }

    @Test
    void setDocumentRequirements_requirementCrossProject_throwsNotFound() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID reqId = UUID.randomUUID();
        RequirementPoolItem foreign = item(UUID.randomUUID(), CREATOR_ID);
        foreign.setId(reqId);
        when(requirementMapper.selectById(reqId)).thenReturn(foreign);
        assertThrows(ServiceException.class,
                () -> service.setDocumentRequirements(DOC_ID, PROJECT_ID, List.of(reqId)));
    }

    @Test
    void setDocumentRequirements_diffAddsAndRemovesKeepingExisting() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID keep = UUID.randomUUID();
        UUID add = UUID.randomUUID();
        UUID remove = UUID.randomUUID();
        // 目标 = {keep, add}，现存 = {keep, remove} → 新增 add、删除 remove、保留 keep
        for (UUID id : List.of(keep, add)) {
            RequirementPoolItem it = item(PROJECT_ID, CREATOR_ID);
            it.setId(id);
            when(requirementMapper.selectById(id)).thenReturn(it);
        }
        when(documentRequirementRelMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(rel(keep), rel(remove)));

        service.setDocumentRequirements(DOC_ID, PROJECT_ID, List.of(keep, add));

        // 删除多余：remove
        ArgumentCaptor<List<UUID>> removeCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRequirementRelMapper).deleteByDocumentIdAndRequirementIds(any(), removeCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(remove), removeCaptor.getValue());
        // 仅新增缺失的 add（keep 保留不动）
        verify(documentRequirementRelMapper, times(1)).insert(any(DocumentRequirementRel.class));
    }

    @Test
    void setDocumentRequirements_emptyTarget_removesAllNoInsert() {
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID existing = UUID.randomUUID();
        when(documentRequirementRelMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(rel(existing)));

        service.setDocumentRequirements(DOC_ID, PROJECT_ID, List.of());

        verify(documentRequirementRelMapper).deleteByDocumentIdAndRequirementIds(any(), any());
        verify(documentRequirementRelMapper, never()).insert(any(DocumentRequirementRel.class));
    }
}
