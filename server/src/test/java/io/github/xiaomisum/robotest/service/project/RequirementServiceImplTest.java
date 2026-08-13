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
import io.github.xiaomisum.robotest.service.ai.gateway.AiConfigService;
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
    void update_archivedItem_throwsNoPermission() {
        RequirementPoolItem archived = item(PROJECT_ID, CREATOR_ID);
        archived.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(archived);
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("改标题");
        assertThrows(ServiceException.class, () -> service.update(ITEM_ID, PROJECT_ID, CREATOR_ID, req));
        // 归档条目只读：不得产生任何更新
        verify(requirementMapper, never()).updateById(any(RequirementPoolItem.class));
    }

    @Test
    void archive_byCreator_setsArchived() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        service.archive(ITEM_ID, PROJECT_ID, CREATOR_ID, true);
        ArgumentCaptor<RequirementPoolItem> captor = ArgumentCaptor.forClass(RequirementPoolItem.class);
        verify(requirementMapper).updateById(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Constants.Status.ARCHIVED, captor.getValue().getStatus());
    }

    @Test
    void archive_unarchive_restoresActive() {
        RequirementPoolItem archived = item(PROJECT_ID, CREATOR_ID);
        archived.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(archived);
        service.archive(ITEM_ID, PROJECT_ID, CREATOR_ID, false);
        ArgumentCaptor<RequirementPoolItem> captor = ArgumentCaptor.forClass(RequirementPoolItem.class);
        verify(requirementMapper).updateById(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Constants.Status.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void archive_sameState_isIdempotentNoUpdate() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        service.archive(ITEM_ID, PROJECT_ID, CREATOR_ID, false);
        verify(requirementMapper, never()).updateById(any(RequirementPoolItem.class));
    }

    @Test
    void archive_byNonCreatorNonAdmin_throwsNoPermission() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(PROJECT_ID, CREATOR_ID));
        stubMember(OTHER_ID, Constants.WorkspaceRole.MEMBER_ID);
        assertThrows(ServiceException.class, () -> service.archive(ITEM_ID, PROJECT_ID, OTHER_ID, true));
    }

    @Test
    void delete_archivedItem_stillAllowed() {
        // 归档即封存只读的是「编辑」，删除不受归档态限制（详细设计 3.1.4）
        RequirementPoolItem archived = item(PROJECT_ID, CREATOR_ID);
        archived.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(archived);
        service.delete(ITEM_ID, PROJECT_ID, CREATOR_ID);
        verify(requirementMapper).deleteById(ITEM_ID);
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

    // ==================== 文档关联（3.1.6） ====================

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
    void getDocumentRequirements_filtersArchivedItems() {
        // 归档条目不参与 AI 消费：关联记录保留，但摘要过滤不展示（需求规格 3.2.4）
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID reqA = UUID.randomUUID();
        UUID reqB = UUID.randomUUID();
        when(documentRequirementRelMapper.listByDocumentId(DOC_ID)).thenReturn(List.of(rel(reqA), rel(reqB)));
        RequirementPoolItem a = item(PROJECT_ID, CREATOR_ID);
        a.setId(reqA);
        a.setTitle("条目A");
        RequirementPoolItem b = item(PROJECT_ID, CREATOR_ID);
        b.setId(reqB);
        b.setTitle("已归档条目B");
        b.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(a, b));

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
    void setDocumentRequirements_archivedItem_throwsNoPermission() {
        // 文档关联仅接受 active 条目：含 archived 拒绝设置（详细设计 3.1.6）
        when(testCaseModuleMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        UUID reqId = UUID.randomUUID();
        RequirementPoolItem archived = item(PROJECT_ID, CREATOR_ID);
        archived.setId(reqId);
        archived.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectById(reqId)).thenReturn(archived);
        assertThrows(ServiceException.class,
                () -> service.setDocumentRequirements(DOC_ID, PROJECT_ID, List.of(reqId)));
        // 校验失败不得产生任何关联写入
        verify(documentRequirementRelMapper, never()).insert(any(DocumentRequirementRel.class));
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

    // ==================== requireByIds（AI 上下文组装，3.2.1） ====================

    @Test
    void requireByIds_emptyOrNull_returnsEmptyWithoutQuery() {
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), service.requireByIds(PROJECT_ID, null));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), service.requireByIds(PROJECT_ID, List.of()));
        verify(requirementMapper, never()).selectBatchIds(any());
    }

    @Test
    void requireByIds_missingItem_throwsNotFound() {
        UUID reqA = UUID.randomUUID();
        UUID reqB = UUID.randomUUID();
        // selectBatchIds 自动过滤已删除条目：reqB 未返回视为缺失
        RequirementPoolItem a = item(PROJECT_ID, CREATOR_ID);
        a.setId(reqA);
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(a));
        assertThrows(ServiceException.class,
                () -> service.requireByIds(PROJECT_ID, List.of(reqA, reqB)));
    }

    @Test
    void requireByIds_crossProject_throwsNotFound() {
        UUID reqId = UUID.randomUUID();
        RequirementPoolItem foreign = item(UUID.randomUUID(), CREATOR_ID);
        foreign.setId(reqId);
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(foreign));
        assertThrows(ServiceException.class, () -> service.requireByIds(PROJECT_ID, List.of(reqId)));
    }

    @Test
    void requireByIds_preservesSelectionOrderAndDeduplicates() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        RequirementPoolItem a = item(PROJECT_ID, CREATOR_ID);
        a.setId(first);
        a.setTitle("条目A");
        RequirementPoolItem b = item(PROJECT_ID, CREATOR_ID);
        b.setId(second);
        b.setTitle("条目B");
        // 模拟按输入顺序去重后查询
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(b, a));

        List<RequirementPoolItem> result = service.requireByIds(PROJECT_ID, List.of(first, second, first));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(first, second),
                result.stream().map(RequirementPoolItem::getId).toList());
    }

    @Test
    void requireByIds_filtersArchivedItems() {
        // 归档条目不参与 AI 上下文组装，静默过滤（需求规格 3.2.4）
        UUID active = UUID.randomUUID();
        UUID archivedId = UUID.randomUUID();
        RequirementPoolItem a = item(PROJECT_ID, CREATOR_ID);
        a.setId(active);
        a.setTitle("条目A");
        RequirementPoolItem b = item(PROJECT_ID, CREATOR_ID);
        b.setId(archivedId);
        b.setTitle("已归档条目B");
        b.setStatus(Constants.Status.ARCHIVED);
        when(requirementMapper.selectBatchIds(anyList())).thenReturn(List.of(a, b));

        List<RequirementPoolItem> result = service.requireByIds(PROJECT_ID, List.of(active, archivedId));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(active),
                result.stream().map(RequirementPoolItem::getId).toList());
    }
}
