package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.requirement.RequirementUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiTaskRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementChangeLogRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitRecordRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSplitSubmitRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementChangeLog;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementChangeLogMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementSplitRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceImplTest {

    /** 纯单测环境无 MyBatis 初始化：UUID 列处理器与 LambdaUpdateWrapper 列解析都依赖框架启动期注册 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Requirement.class);
    }

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private RequirementChangeLogMapper changeLogMapper;
    @Mock
    private RequirementSplitRecordMapper splitRecordMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ImpactAnalysisTaskPublisher impactAnalysisTaskPublisher;
    @Mock
    private AiTaskService aiTaskService;
    @Mock
    private AiTaskMapper aiTaskMapper;

    /** 真实编号分配逻辑（savepoint 重试）：@Spy+@InjectMocks 组合会把非 mock 实例带入候选集，
     *  NameBasedCandidateFilter#getMockName 对非 mock 抛 NotAMock，故手工装配 */
    private RequirementCodeAllocator codeAllocator;

    @InjectMocks
    private RequirementServiceImpl service;

    @BeforeEach
    void wireCodeAllocator() {
        // initMocks（扩展回调）先于 @BeforeEach 执行，此时 service 已注入 @Mock
        codeAllocator = new RequirementCodeAllocator();
        setField(codeAllocator, "requirementMapper", requirementMapper);
        setField(codeAllocator, "transactionManager", transactionManager);
        setField(service, "codeAllocator", codeAllocator);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubTx() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private Requirement item(String status) {
        Requirement r = new Requirement();
        r.setId(ITEM_ID);
        r.setProjectId(PROJECT_ID);
        r.setCode("REQ-001");
        r.setTitle("登录验证码");
        r.setDescription("描述正文");
        r.setStatus(status);
        r.setModuleId(MODULE_ID);
        r.setOwnerId(OWNER_ID);
        r.setPriority("high");
        r.setSystemVersion("V2.3");
        return r;
    }

    private void stubItem(String status) {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(item(status));
    }

    private void stubModule() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setProjectId(PROJECT_ID);
        module.setName("登录模块");
        when(projectModuleMapper.selectById(MODULE_ID)).thenReturn(module);
    }

    private void stubOwner() {
        SysUser user = new SysUser();
        user.setId(OWNER_ID);
        user.setName("张三");
        when(userMapper.selectById(OWNER_ID)).thenReturn(user);
    }

    private void stubMember() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(UUID.randomUUID());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), OWNER_ID))
                .thenReturn(new WorkspaceUser());
    }

    private RequirementCreateReqDTO createReq() {
        RequirementCreateReqDTO dto = new RequirementCreateReqDTO();
        dto.setTitle("登录验证码");
        dto.setDescription("描述正文");
        return dto;
    }

    // ---------- 创建与编号分配 ----------

    @Test
    void create_allocatesCodeAndDraftStatus() {
        stubTx();
        stubModule();
        stubOwner();
        stubMember();
        when(requirementMapper.selectMaxSeq(PROJECT_ID)).thenReturn(0);

        RequirementCreateReqDTO req = createReq();
        req.setModuleId(MODULE_ID);
        req.setOwnerId(OWNER_ID);
        req.setPriority("high");
        req.setSystemVersion("V2.3");
        RequirementDetailRespDTO detail = service.create(PROJECT_ID, OPERATOR_ID, req);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).insert(captor.capture());
        Requirement saved = captor.getValue();
        assertEquals("REQ-001", saved.getCode());
        assertEquals("draft", saved.getStatus());
        assertEquals("manual", saved.getSource());
        assertEquals("REQ-001", detail.getCode());
        assertEquals("登录模块", detail.getModuleName());
        assertEquals("张三", detail.getOwnerName());
        assertNull(detail.getCoverageStatus());
        assertNull(detail.getSourceFile());
    }

    @Test
    void create_duplicateKeyRetriesWithNextSeq() {
        stubTx();
        when(requirementMapper.selectMaxSeq(PROJECT_ID)).thenReturn(0, 1, 2);
        when(requirementMapper.insert(any(Requirement.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenAnswer(inv -> 1);

        service.create(PROJECT_ID, OPERATOR_ID, createReq());

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper, times(3)).insert(captor.capture());
        assertEquals("REQ-003", captor.getValue().getCode());
    }

    @Test
    void create_duplicateKeyRetriesExhausted_throwsCodeConflict() {
        stubTx();
        when(requirementMapper.selectMaxSeq(PROJECT_ID)).thenReturn(0);
        when(requirementMapper.insert(any(Requirement.class))).thenThrow(new DuplicateKeyException("dup"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, OPERATOR_ID, createReq()));
        assertEquals(ErrorCodeConstants.REQUIREMENT_CODE_CONFLICT.code(), exception.getCode());
        verify(requirementMapper, times(4)).insert(any(Requirement.class));
    }

    @Test
    void create_moduleNotInProject_throwsModuleNotFound() {
        when(projectModuleMapper.selectById(MODULE_ID)).thenReturn(new ProjectModule());
        RequirementCreateReqDTO req = createReq();
        req.setModuleId(MODULE_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, OPERATOR_ID, req));
        assertEquals(ErrorCodeConstants.REQUIREMENT_MODULE_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void create_invalidPriority_throwsAttributeInvalid() {
        RequirementCreateReqDTO req = createReq();
        req.setPriority("urgent");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, OPERATOR_ID, req));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID.code(), exception.getCode());
    }

    @Test
    void create_systemVersionTooLong_throwsAttributeInvalid() {
        RequirementCreateReqDTO req = createReq();
        req.setSystemVersion("V".repeat(51));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, OPERATOR_ID, req));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID.code(), exception.getCode());
    }

    @Test
    void create_ownerNotWorkspaceMember_throwsAttributeInvalid() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(UUID.randomUUID());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(project.getWorkspaceId(), OWNER_ID)).thenReturn(null);
        RequirementCreateReqDTO req = createReq();
        req.setOwnerId(OWNER_ID);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(PROJECT_ID, OPERATOR_ID, req));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID.code(), exception.getCode());
    }

    // ---------- 列表 ----------

    @Test
    void page_returnsItemsWithResolvedNames() {
        when(requirementMapper.findPage(any(), eq(PROJECT_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(item("confirmed")), 1L));
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setName("登录模块");
        when(projectModuleMapper.selectBatchIds(List.of(MODULE_ID))).thenReturn(List.of(module));
        SysUser user = new SysUser();
        user.setId(OWNER_ID);
        user.setName("张三");
        when(userMapper.selectBatchIds(List.of(OWNER_ID))).thenReturn(List.of(user));

        PageResult<RequirementListRespDTO> result = service.page(new RequirementPageReqDTO(), PROJECT_ID);

        assertEquals(1, result.getList().size());
        RequirementListRespDTO dto = result.getList().get(0);
        assertEquals("REQ-001", dto.getCode());
        assertEquals("登录模块", dto.getModuleName());
        assertEquals("张三", dto.getOwnerName());
        assertNull(dto.getCoverageStatus());
    }

    @Test
    void page_empty_skipsResolution() {
        when(requirementMapper.findPage(any(), eq(PROJECT_ID), any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0L));

        PageResult<RequirementListRespDTO> result = service.page(new RequirementPageReqDTO(), PROJECT_ID);

        assertTrue(result.getList().isEmpty());
        verifyNoInteractions(projectModuleMapper, userMapper);
    }

    @Test
    void page_invalidModuleIdFilter_throwsValidationFailed() {
        RequirementPageReqDTO req = new RequirementPageReqDTO();
        req.setModuleIds("not-a-uuid");

        ServiceException exception = assertThrows(ServiceException.class, () -> service.page(req, PROJECT_ID));
        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    // ---------- 详情 ----------

    @Test
    void getDetail_crossProject_throwsNotFound() {
        Requirement foreign = item("confirmed");
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(foreign);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getDetail(ITEM_ID, PROJECT_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void getDetail_resolvesModuleAndOwner() {
        stubItem("confirmed");
        stubModule();
        stubOwner();

        RequirementDetailRespDTO detail = service.getDetail(ITEM_ID, PROJECT_ID);

        assertEquals("登录模块", detail.getModuleName());
        assertEquals("张三", detail.getOwnerName());
        assertEquals("REQ-001", detail.getCode());
    }

    // ---------- 部分更新 ----------

    @Test
    void update_archived_throwsReadonly() {
        stubItem("archived");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, new RequirementUpdateReqDTO()));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ARCHIVED_READONLY.code(), exception.getCode());
    }

    @Test
    void update_titleOnConfirmed_flipsToChangedAndPublishes() {
        stubItem("confirmed");
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("登录验证码（短信+邮箱）");

        RequirementDetailRespDTO detail = service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, req);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        Requirement carrier = captor.getValue();
        assertEquals("changed", carrier.getStatus());
        assertEquals("登录验证码（短信+邮箱）", carrier.getTitle());
        assertNull(carrier.getPriority());
        assertNull(carrier.getSystemVersion());

        ArgumentCaptor<RequirementChangeLog> logCaptor = ArgumentCaptor.forClass(RequirementChangeLog.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertEquals("title", logCaptor.getValue().getChangeType());
        verify(impactAnalysisTaskPublisher).publish(ITEM_ID, PROJECT_ID, OPERATOR_ID);
        assertNotNull(detail.getId());
    }

    @Test
    void update_publisherFailure_doesNotFailUpdate() {
        stubItem("confirmed");
        doThrow(new RuntimeException("submit failed")).when(impactAnalysisTaskPublisher)
                .publish(any(), any(), any());
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("新标题");

        service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, req);

        verify(requirementMapper).updateById(any(Requirement.class));
    }

    @Test
    void update_attributeOnly_keepsStatusAndWritesAttributeLog() {
        stubItem("confirmed");
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setPriority("medium");

        service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, req);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertNull(captor.getValue().getStatus());
        assertEquals("medium", captor.getValue().getPriority());

        ArgumentCaptor<RequirementChangeLog> logCaptor = ArgumentCaptor.forClass(RequirementChangeLog.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertEquals("attribute", logCaptor.getValue().getChangeType());
        verifyNoInteractions(impactAnalysisTaskPublisher);
    }

    @Test
    void update_titleOnDraft_writesFieldLogWithoutFlip() {
        stubItem("draft");
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setTitle("新标题");

        service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, req);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertNull(captor.getValue().getStatus());
        verify(changeLogMapper).insert(any(RequirementChangeLog.class));
        verifyNoInteractions(impactAnalysisTaskPublisher);
    }

    @Test
    void update_clearsSystemVersionViaWrapper() {
        stubItem("confirmed");
        RequirementUpdateReqDTO req = new RequirementUpdateReqDTO();
        req.setSystemVersion("  ");

        service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, req);

        verify(requirementMapper).update(isNull(), any(LambdaUpdateWrapperX.class));
        verify(changeLogMapper).insert(any(RequirementChangeLog.class));
        verifyNoInteractions(impactAnalysisTaskPublisher);
    }

    @Test
    void update_nothingChanged_skipsUpdateAndLog() {
        stubItem("confirmed");

        service.update(ITEM_ID, PROJECT_ID, OPERATOR_ID, new RequirementUpdateReqDTO());

        verify(requirementMapper, never()).updateById(any(Requirement.class));
        verifyNoInteractions(changeLogMapper);
    }

    // ---------- 状态机 ----------

    @Test
    void confirm_draftToConfirmed() {
        stubItem("draft");

        service.confirm(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertEquals("confirmed", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getConfirmedAt());
        verify(changeLogMapper).insert(any(RequirementChangeLog.class));
        verifyNoInteractions(impactAnalysisTaskPublisher);
    }

    @Test
    void confirm_fromChanged_publishesImpactRefresh() {
        stubItem("changed");

        service.confirm(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        verify(impactAnalysisTaskPublisher).publish(ITEM_ID, PROJECT_ID, OPERATOR_ID);
    }

    @Test
    void confirm_archived_throwsStatusNotAllowed() {
        stubItem("archived");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.confirm(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED.code(), exception.getCode());
        verify(requirementMapper, never()).updateById(any(Requirement.class));
    }

    @Test
    void archive_fromDraft_writesStatusLog() {
        stubItem("draft");

        service.archive(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertEquals("archived", captor.getValue().getStatus());
        verify(changeLogMapper).insert(any(RequirementChangeLog.class));
    }

    @Test
    void archive_alreadyArchived_throwsStatusNotAllowed() {
        stubItem("archived");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.archive(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED.code(), exception.getCode());
    }

    @Test
    void unarchive_returnsToDraft() {
        stubItem("archived");

        service.unarchive(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper).updateById(captor.capture());
        assertEquals("draft", captor.getValue().getStatus());
    }

    @Test
    void unarchive_nonArchived_throwsStatusNotAllowed() {
        stubItem("confirmed");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.unarchive(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_STATUS_NOT_ALLOWED.code(), exception.getCode());
    }

    // ---------- 变更记录与拆解记录 ----------

    @Test
    void getChangeLogs_enrichesOperatorName() {
        stubItem("confirmed");
        RequirementChangeLog entry = new RequirementChangeLog();
        entry.setId(UUID.randomUUID());
        entry.setRequirementId(ITEM_ID);
        entry.setOperatorId(OPERATOR_ID);
        entry.setChangeType("title");
        when(changeLogMapper.findPage(any(), eq(ITEM_ID))).thenReturn(new PageResult<>(List.of(entry), 1L));
        SysUser operator = new SysUser();
        operator.setId(OPERATOR_ID);
        operator.setName("李四");
        when(userMapper.selectBatchIds(List.of(OPERATOR_ID))).thenReturn(List.of(operator));

        PageResult<RequirementChangeLogRespDTO> result = service.getChangeLogs(ITEM_ID, PROJECT_ID,
                new PageParam());

        assertEquals("李四", result.getList().get(0).getOperatorName());
        assertEquals("title", result.getList().get(0).getChangeType());
    }

    @Test
    void getSplitLogs_crossProject_throwsNotFound() {
        Requirement foreign = item("confirmed");
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(foreign);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getSplitLogs(ITEM_ID, PROJECT_ID, new PageParam()));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void getSplitRecords_resolvesSourceRequirementCode() {
        RequirementSplitRecord record = new RequirementSplitRecord();
        record.setId(UUID.randomUUID());
        record.setSourceType("requirement");
        record.setSourceRequirementId(ITEM_ID);
        record.setStatus("adopted");
        when(splitRecordMapper.findPage(any(), eq(PROJECT_ID), any(), any()))
                .thenReturn(new PageResult<>(List.of(record), 1L));
        Requirement source = item("archived");
        when(requirementMapper.selectBatchIds(List.of(ITEM_ID))).thenReturn(List.of(source));

        PageResult<RequirementSplitRecordRespDTO> result = service.getSplitRecords(PROJECT_ID, null, null,
                new PageParam());

        assertEquals("REQ-001", result.getList().get(0).getSourceRequirementCode());
        assertEquals("adopted", result.getList().get(0).getStatus());
    }

    // ---------- 条目内 AI 拆分（3.9） ----------

    private void stubProject() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(UUID.randomUUID());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
    }

    private void stubSplitRecordInserted() {
        doAnswer(inv -> {
            RequirementSplitRecord record = inv.getArgument(0);
            record.setId(RECORD_ID);
            return 1;
        }).when(splitRecordMapper).insert(any(RequirementSplitRecord.class));
    }

    @Test
    void split_submitsTaskAndCreatesRecord() {
        stubItem("draft");
        stubProject();
        stubSplitRecordInserted();
        when(splitRecordMapper.selectPendingSplitBySource(ITEM_ID)).thenReturn(List.of());
        AiTaskRespDTO task = new AiTaskRespDTO();
        task.setTaskId(TASK_ID);
        when(aiTaskService.submitInternal(eq("requirement_split"), any(), eq(OPERATOR_ID), eq(PROJECT_ID), any()))
                .thenReturn(task);

        RequirementSplitSubmitRespDTO resp = service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        assertEquals(TASK_ID, resp.getTaskId());
        assertEquals(RECORD_ID, resp.getSplitRecordId());
        assertEquals("pending", resp.getStatus());
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiTaskService).submitInternal(eq("requirement_split"), inputCaptor.capture(),
                eq(OPERATOR_ID), eq(PROJECT_ID), any());
        assertEquals(ITEM_ID.toString(), inputCaptor.getValue().get("requirementId"));
        ArgumentCaptor<RequirementSplitRecord> recordCaptor = ArgumentCaptor.forClass(RequirementSplitRecord.class);
        verify(splitRecordMapper).insert(recordCaptor.capture());
        RequirementSplitRecord saved = recordCaptor.getValue();
        assertEquals("requirement", saved.getSourceType());
        assertEquals(ITEM_ID, saved.getSourceRequirementId());
        assertEquals(TASK_ID, saved.getAiTaskId());
        assertEquals("pending", saved.getStatus());
        assertEquals(PROJECT_ID, saved.getProjectId());
    }

    @Test
    void split_archivedItem_throwsSplitInputInvalid() {
        stubItem("archived");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_SPLIT_INPUT_INVALID.code(), exception.getCode());
        verifyNoInteractions(aiTaskService, splitRecordMapper);
    }

    @Test
    void split_inProgressTask_throwsTaskInProgress() {
        stubItem("draft");
        RequirementSplitRecord record = new RequirementSplitRecord();
        record.setId(UUID.randomUUID());
        record.setAiTaskId(TASK_ID);
        when(splitRecordMapper.selectPendingSplitBySource(ITEM_ID)).thenReturn(List.of(record));
        AiTask running = new AiTask();
        running.setId(TASK_ID);
        running.setStatus("running");
        when(aiTaskMapper.selectById(TASK_ID)).thenReturn(running);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_TASK_IN_PROGRESS.code(), exception.getCode());
        verifyNoInteractions(aiTaskService);
    }

    @Test
    void split_failedTaskRecordAllowsResubmit() {
        stubItem("draft");
        stubProject();
        stubSplitRecordInserted();
        RequirementSplitRecord stale = new RequirementSplitRecord();
        stale.setId(UUID.randomUUID());
        stale.setAiTaskId(TASK_ID);
        when(splitRecordMapper.selectPendingSplitBySource(ITEM_ID)).thenReturn(List.of(stale));
        AiTask failed = new AiTask();
        failed.setId(TASK_ID);
        failed.setStatus("failed");
        when(aiTaskMapper.selectById(TASK_ID)).thenReturn(failed);
        AiTaskRespDTO task = new AiTaskRespDTO();
        task.setTaskId(TASK_ID);
        when(aiTaskService.submitInternal(eq("requirement_split"), any(), any(), any(), any())).thenReturn(task);

        service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID);

        verify(aiTaskService).submitInternal(eq("requirement_split"), any(), eq(OPERATOR_ID), eq(PROJECT_ID), any());
    }

    @Test
    void split_notFound_throwsNotFound() {
        when(requirementMapper.selectById(ITEM_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verifyNoInteractions(aiTaskService, splitRecordMapper);
    }

    @Test
    void split_submitFails_recordNotInserted() {
        stubItem("draft");
        stubProject();
        when(splitRecordMapper.selectPendingSplitBySource(ITEM_ID)).thenReturn(List.of());
        when(aiTaskService.submitInternal(any(), any(), any(), any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_DISABLED));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.split(ITEM_ID, PROJECT_ID, OPERATOR_ID));
        assertEquals(ErrorCodeConstants.AI_DISABLED.code(), exception.getCode());
        verify(splitRecordMapper, never()).insert(any(RequirementSplitRecord.class));
    }
}
