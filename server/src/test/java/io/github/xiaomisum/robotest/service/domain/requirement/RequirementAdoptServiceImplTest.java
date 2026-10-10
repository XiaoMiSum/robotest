package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementChangeLog;
import io.github.xiaomisum.robotest.model.entity.requirement.RequirementSplitRecord;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.requirement.RequirementChangeLogMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.requirement.RequirementSplitRecordMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import xyz.migoo.framework.mybatis.core.LambdaUpdateWrapperX;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAdoptServiceImplTest {

    /** 纯单测无 MyBatis 启动期注册：wrapper 列解析与 UUID 处理器需手工初始化 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Requirement.class);
        TableInfoHelper.initTableInfo(assistant, RequirementSplitRecord.class);
    }

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID CREATED_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private VectorIndexService vectorIndexService;
    @Mock
    private RequirementSplitRecordMapper splitRecordMapper;
    @Mock
    private RequirementChangeLogMapper changeLogMapper;
    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private RequirementCodeAllocator codeAllocator;

    @InjectMocks
    private RequirementAdoptServiceImpl service;

    // ---------- 测试构件 ----------

    private AiTask task(List<Map<String, Object>> artifacts) {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setType("requirement_split");
        task.setProjectId(PROJECT_ID);
        task.setStatus("succeeded");
        task.setInput(Map.of("requirementId", SOURCE_ID.toString()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifacts", artifacts);
        task.setResult(result);
        return task;
    }

    private Map<String, Object> artifact(String key, String title, Object moduleId, String priority) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", title);
        content.put("description", "拆分出的描述");
        content.put("moduleId", moduleId);
        content.put("priority", priority);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("kind", "requirement_suggestion");
        map.put("title", title);
        map.put("content", content);
        return map;
    }

    private AdoptContext ctx(AiTask task, Map<String, Object> artifact, String action,
            Map<String, Object> content, String targetSystemVersion) {
        return new AdoptContext(task, artifact, action, content, null, null, null,
                targetSystemVersion, null, null, null, null, null, PROJECT_ID, OPERATOR_ID, null);
    }

    private RequirementSplitRecord record() {
        RequirementSplitRecord record = new RequirementSplitRecord();
        record.setId(RECORD_ID);
        record.setProjectId(PROJECT_ID);
        record.setSourceType("requirement");
        record.setSourceRequirementId(SOURCE_ID);
        record.setAiTaskId(TASK_ID);
        record.setStatus("pending");
        return record;
    }

    private Requirement source(String status, String systemVersion) {
        Requirement source = new Requirement();
        source.setId(SOURCE_ID);
        source.setProjectId(PROJECT_ID);
        source.setStatus(status);
        source.setSystemVersion(systemVersion);
        source.setCode("REQ-007");
        source.setTitle("原需求");
        return source;
    }

    private void stubRecordAndModule(RequirementSplitRecord record, Requirement source) {
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(record));
        when(requirementMapper.selectById(SOURCE_ID)).thenReturn(source);
    }

    private void stubModule() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setProjectId(PROJECT_ID);
        module.setName("登录模块");
        when(projectModuleMapper.selectById(MODULE_ID)).thenReturn(module);
    }

    private void stubAllocator() {
        doAnswer(inv -> {
            Requirement saved = inv.getArgument(1);
            saved.setId(CREATED_ID);
            return null;
        }).when(codeAllocator).insertWithCodeAllocation(any(), any(Requirement.class));
    }

    /** wrapper 显式 set 的值（含 merged adoptResult 与 status）中是否含目标值 */
    @SuppressWarnings("unchecked")
    private static boolean wrapperContains(Object wrapper, Object value) {
        return ((LambdaUpdateWrapperX<?>) wrapper).getParamNameValuePairs().containsValue(value);
    }

    // ---------- 采纳落库 ----------

    @Test
    void adopt_adopted_createsConfirmedRequirementAndStaysPending() {
        AiTask task = task(List.of(artifact("item-1", "建议一", MODULE_ID.toString(), "high"),
                artifact("item-2", "建议二", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", MODULE_ID.toString(), "high");
        stubRecordAndModule(record(), source("confirmed", "V2.3"));
        stubModule();
        stubAllocator();

        AdoptOutcome outcome = service.adopt(ctx(task, art, "adopted", null, null));

        assertNotNull(outcome.createdId());
        assertEquals(CREATED_ID, outcome.createdId());
        assertEquals(CREATED_ID.toString(), outcome.adoptedRef().get("requirementId"));
        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(eq(PROJECT_ID), captor.capture());
        Requirement saved = captor.getValue();
        assertEquals("confirmed", saved.getStatus());
        assertEquals("requirement", saved.getSource());
        assertEquals("建议一", saved.getTitle());
        assertEquals(MODULE_ID, saved.getModuleId());
        assertEquals("high", saved.getPriority());
        assertEquals("V2.3", saved.getSystemVersion());
        assertNotNull(saved.getConfirmedAt());
        assertNull(saved.getSourceFileId());
        // 3 项产物只处理 1 项 → pending，不归档
        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "pending"));
        verify(requirementMapper, never()).update(isNull(), any());
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    void adopt_adoptedEdited_usesEditedContentAndTargetVersion() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        Map<String, Object> edited = new LinkedHashMap<>();
        edited.put("title", "编辑后的标题");
        edited.put("priority", "medium");
        edited.put("moduleId", null);
        stubRecordAndModule(record(), source("confirmed", "V2.3"));
        stubAllocator();

        AdoptOutcome outcome = service.adopt(ctx(task, art, "adopted_edited", edited, "V9"));

        assertEquals(CREATED_ID, outcome.createdId());
        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(any(), captor.capture());
        assertEquals("编辑后的标题", captor.getValue().getTitle());
        assertEquals("medium", captor.getValue().getPriority());
        assertEquals("V9", captor.getValue().getSystemVersion());
        assertNull(captor.getValue().getModuleId());
    }

    @Test
    void adopt_targetVersionBlank_clearsInheritedVersion() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        stubRecordAndModule(record(), source("confirmed", "V2.3"));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, "  "));

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(any(), captor.capture());
        assertNull(captor.getValue().getSystemVersion());
    }

    @Test
    void adopt_rejected_recordsRejectWithoutCreating() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null),
                artifact("item-2", "建议二", null, null)));
        Map<String, Object> art = artifact("item-2", "建议二", null, null);
        RequirementSplitRecord record = record();
        Map<String, Object> prior = new LinkedHashMap<>();
        prior.put("adopted", List.of());
        prior.put("rejected", List.of("item-1"));
        record.setAdoptResult(prior);
        stubRecordAndModule(record, source("confirmed", null));

        AdoptOutcome outcome = service.adopt(ctx(task, art, "rejected", null, null));

        assertNull(outcome.createdId());
        assertNull(outcome.adoptedRef());
        verifyNoInteractions(codeAllocator);
        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        // 两项全部驳回 → rejected（4.5.3），不产生需求也不归档
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "rejected"));
        verify(requirementMapper, never()).update(isNull(), any());
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    void adopt_lastItemAdopted_adoptsStatusAndArchivesSource() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null),
                artifact("item-2", "建议二", null, null)));
        Map<String, Object> art = artifact("item-2", "建议二", null, null);
        RequirementSplitRecord record = record();
        Map<String, Object> prior = new LinkedHashMap<>();
        prior.put("adopted", List.of(Map.of("artifactKey", "item-1", "requirementId", CREATED_ID.toString())));
        prior.put("rejected", List.of());
        record.setAdoptResult(prior);
        stubRecordAndModule(record, source("confirmed", "V2.3"));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "adopted"));
        verify(requirementMapper).update(isNull(), any());
        ArgumentCaptor<RequirementChangeLog> logCaptor = ArgumentCaptor.forClass(RequirementChangeLog.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertEquals("status", logCaptor.getValue().getChangeType());
        assertEquals(SOURCE_ID, logCaptor.getValue().getRequirementId());
        assertEquals(OPERATOR_ID, logCaptor.getValue().getOperatorId());
        assertEquals(Map.of("status", "confirmed"), logCaptor.getValue().getBeforeSummary());
        assertEquals(Map.of("status", "archived"), logCaptor.getValue().getAfterSummary());
    }

    @Test
    void adopt_allRejected_marksRejectedWithoutArchive() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null),
                artifact("item-2", "建议二", null, null)));
        Map<String, Object> art = artifact("item-2", "建议二", null, null);
        RequirementSplitRecord record = record();
        Map<String, Object> prior = new LinkedHashMap<>();
        prior.put("adopted", List.of());
        prior.put("rejected", List.of("item-1"));
        record.setAdoptResult(prior);
        stubRecordAndModule(record, source("confirmed", null));

        service.adopt(ctx(task, art, "rejected", null, null));

        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "rejected"));
        verify(requirementMapper, never()).update(isNull(), any());
        verifyNoInteractions(changeLogMapper);
    }

    @Test
    void adopt_recordAlreadyArchivedSource_skipsReArchive() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        stubRecordAndModule(record(), source("archived", null));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        verify(requirementMapper, never()).update(isNull(), any());
        verifyNoInteractions(changeLogMapper);
    }

    // ---------- 3.8 导入采纳 ----------

    private AiTask importTask(List<Map<String, Object>> artifacts, Map<String, Object> documentMeta) {
        AiTask task = new AiTask();
        task.setId(TASK_ID);
        task.setType("requirement_import");
        task.setProjectId(PROJECT_ID);
        task.setInput(Map.of("fileId", FILE_ID.toString()));
        Map<String, Object> result = new LinkedHashMap<>();
        if (documentMeta != null) {
            result.put("documentMeta", documentMeta);
        }
        result.put("artifacts", artifacts);
        task.setResult(result);
        task.setStatus("succeeded");
        return task;
    }

    private AiTask importTask(List<Map<String, Object>> artifacts) {
        return importTask(artifacts,
                Map.of("detectedVersion", "V3.0", "versionEvidence", "文档版本为 V3.0"));
    }

    private RequirementSplitRecord importRecord() {
        RequirementSplitRecord record = new RequirementSplitRecord();
        record.setId(RECORD_ID);
        record.setProjectId(PROJECT_ID);
        record.setSourceType("document");
        record.setSourceFileId(FILE_ID);
        record.setAiTaskId(TASK_ID);
        record.setStatus("pending");
        return record;
    }

    @Test
    void adopt_importTask_writesImportSourceAndDetectsVersion() {
        AiTask task = importTask(List.of(artifact("item-1", "建议一", null, "high")));
        Map<String, Object> art = artifact("item-1", "建议一", null, "high");
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(importRecord()));
        stubAllocator();

        AdoptOutcome outcome = service.adopt(ctx(task, art, "adopted", null, null));

        assertNotNull(outcome.createdId());
        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(eq(PROJECT_ID), captor.capture());
        Requirement saved = captor.getValue();
        assertEquals("import", saved.getSource());
        assertEquals(FILE_ID, saved.getSourceFileId());
        assertEquals("V3.0", saved.getSystemVersion()); // documentMeta.detectedVersion 回退（4.5）
        assertEquals("confirmed", saved.getStatus());
        // 导入无原条目：不归档、不写变更日志
        verify(requirementMapper, never()).update(isNull(), any());
        verifyNoInteractions(changeLogMapper);
        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "adopted"));
    }

    @Test
    void adopt_importTask_targetVersionOverridesDetectedVersion() {
        AiTask task = importTask(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(importRecord()));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, "V9"));

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(any(), captor.capture());
        assertEquals("V9", captor.getValue().getSystemVersion()); // 确认面板值优先
    }

    @Test
    void adopt_importTask_targetBlank_clearsDetectedVersion() {
        AiTask task = importTask(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(importRecord()));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, "  "));

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(any(), captor.capture());
        assertNull(captor.getValue().getSystemVersion()); // 空白为显式清空
    }

    @Test
    void adopt_importTaskWithoutDocumentMeta_leavesVersionEmpty() {
        AiTask task = importTask(List.of(artifact("item-1", "建议一", null, null)), null);
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(importRecord()));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        ArgumentCaptor<Requirement> captor = ArgumentCaptor.forClass(Requirement.class);
        verify(codeAllocator).insertWithCodeAllocation(any(), captor.capture());
        assertNull(captor.getValue().getSystemVersion()); // 识别不到留空待手工补录（3.8）
    }

    @Test
    void adopt_importRecordMissing_createsDocumentRecord() {
        AiTask task = importTask(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of());
        when(splitRecordMapper.selectPendingImportByProject(PROJECT_ID)).thenReturn(List.of());
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        ArgumentCaptor<RequirementSplitRecord> recordCaptor = ArgumentCaptor.forClass(RequirementSplitRecord.class);
        verify(splitRecordMapper).insert(recordCaptor.capture());
        RequirementSplitRecord saved = recordCaptor.getValue();
        assertEquals("document", saved.getSourceType());
        assertEquals(FILE_ID, saved.getSourceFileId());
        assertEquals(TASK_ID, saved.getAiTaskId());
        assertEquals("adopted", saved.getStatus());
        verify(splitRecordMapper, never()).update(isNull(), any());
    }

    // ---------- 记录定位 ----------

    @Test
    void adopt_recordMissing_ensuresRecordViaInsert() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of());
        when(splitRecordMapper.selectPendingSplitBySource(SOURCE_ID)).thenReturn(List.of());
        when(requirementMapper.selectById(SOURCE_ID)).thenReturn(source("confirmed", null));
        stubAllocator();
        ArgumentCaptor<RequirementSplitRecord> recordCaptor = ArgumentCaptor.forClass(RequirementSplitRecord.class);

        service.adopt(ctx(task, art, "adopted", null, null));

        verify(splitRecordMapper).insert(recordCaptor.capture());
        RequirementSplitRecord saved = recordCaptor.getValue();
        assertEquals(PROJECT_ID, saved.getProjectId());
        assertEquals("requirement", saved.getSourceType());
        assertEquals(SOURCE_ID, saved.getSourceRequirementId());
        assertEquals(TASK_ID, saved.getAiTaskId());
        assertEquals("adopted", saved.getStatus());
        assertNotNull(saved.getAdoptResult());
        verify(splitRecordMapper, never()).update(isNull(), any());
    }

    @Test
    void adopt_retryFallback_repointsRecordToCurrentTask() {
        UUID retriedTaskId = UUID.randomUUID();
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        task.setId(retriedTaskId);
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        RequirementSplitRecord record = record();
        record.setAiTaskId(UUID.randomUUID()); // 仍指原（失败）任务
        when(splitRecordMapper.selectByAiTaskId(retriedTaskId)).thenReturn(List.of());
        when(splitRecordMapper.selectPendingSplitBySource(SOURCE_ID)).thenReturn(List.of(record));
        when(requirementMapper.selectById(SOURCE_ID)).thenReturn(source("confirmed", null));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), retriedTaskId));
        verify(splitRecordMapper, never()).insert(any(RequirementSplitRecord.class));
    }

    // ---------- 校验失败 ----------

    @Test
    void adopt_inputMissingRequirementId_throwsInputInvalid() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        task.setInput(Map.of());
        Map<String, Object> art = artifact("item-1", "建议一", null, null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.adopt(ctx(task, art, "adopted", null, null)));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verifyNoInteractions(splitRecordMapper, requirementMapper, codeAllocator);
    }

    @Test
    void adopt_blankTitle_throwsAttributeInvalid() {
        AiTask task = task(List.of(artifact("item-1", "  ", null, null)));
        Map<String, Object> art = artifact("item-1", "  ", null, null);
        stubRecordAndModule(record(), source("confirmed", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.adopt(ctx(task, art, "adopted", null, null)));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID.code(), exception.getCode());
        verifyNoInteractions(codeAllocator);
    }

    @Test
    void adopt_moduleNotInProject_throwsModuleNotFound() {
        AiTask task = task(List.of(artifact("item-1", "建议一", UUID.randomUUID().toString(), null)));
        Map<String, Object> art = artifact("item-1", "建议一", UUID.randomUUID().toString(), null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(record()));
        when(requirementMapper.selectById(SOURCE_ID)).thenReturn(source("confirmed", null));
        when(projectModuleMapper.selectById(any(UUID.class))).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.adopt(ctx(task, art, "adopted", null, null)));
        assertEquals(ErrorCodeConstants.REQUIREMENT_MODULE_NOT_FOUND.code(), exception.getCode());
        verifyNoInteractions(codeAllocator);
    }

    @Test
    void adopt_sourceCrossProject_throwsNotFound() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        when(splitRecordMapper.selectByAiTaskId(TASK_ID)).thenReturn(List.of(record()));
        Requirement foreign = source("confirmed", null);
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.selectById(SOURCE_ID)).thenReturn(foreign);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.adopt(ctx(task, art, "adopted", null, null)));
        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verifyNoInteractions(codeAllocator, projectModuleMapper, changeLogMapper);
    }

    @Test
    void adopt_invalidPriority_throwsAttributeInvalid() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, "urgent")));
        Map<String, Object> art = artifact("item-1", "建议一", null, "urgent");
        stubRecordAndModule(record(), source("confirmed", null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.adopt(ctx(task, art, "adopted", null, null)));
        assertEquals(ErrorCodeConstants.REQUIREMENT_ATTRIBUTE_INVALID.code(), exception.getCode());
        verifyNoInteractions(codeAllocator);
    }

    @Test
    void adopt_adoptedRefTracksCreatedRequirementId() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null)));
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        stubRecordAndModule(record(), source("confirmed", null));
        stubAllocator();

        AdoptOutcome outcome = service.adopt(ctx(task, art, "adopted", null, null));

        assertEquals(CREATED_ID, outcome.createdId());
        Map<String, Object> adopted = new HashMap<>(outcome.adoptedRef());
        assertEquals(CREATED_ID.toString(), adopted.get("requirementId"));
    }

    @Test
    void adopt_emptyArtifactsTask_staysPending() {
        AiTask task = task(List.of());
        Map<String, Object> art = artifact("item-1", "建议一", null, null);
        stubRecordAndModule(record(), source("confirmed", null));
        stubAllocator();

        service.adopt(ctx(task, art, "adopted", null, null));

        // result.artifacts 为空（键集不可计算）→ 不判定完成，不归档
        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        assertTrue(wrapperContains(wrapperCaptor.getValue(), "pending"));
        verify(requirementMapper, never()).update(isNull(), any());
    }

    @Test
    void adopt_priorAdoptedKeysPreservedInMergedResult() {
        AiTask task = task(List.of(artifact("item-1", "建议一", null, null),
                artifact("item-2", "建议二", null, null)));
        Map<String, Object> art = artifact("item-2", "建议二", null, null);
        RequirementSplitRecord record = record();
        List<Object> priorAdopted = new ArrayList<>();
        priorAdopted.add(Map.of("artifactKey", "item-1", "requirementId", CREATED_ID.toString()));
        Map<String, Object> prior = new LinkedHashMap<>();
        prior.put("adopted", priorAdopted);
        prior.put("rejected", List.of());
        record.setAdoptResult(prior);
        stubRecordAndModule(record, source("confirmed", null));

        service.adopt(ctx(task, art, "rejected", null, null));

        ArgumentCaptor<Wrapper<RequirementSplitRecord>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(splitRecordMapper).update(isNull(), wrapperCaptor.capture());
        Object value = findParam(wrapperCaptor.getValue(), Map.class);
        assertNotNull(value);
        @SuppressWarnings("unchecked")
        Map<String, Object> merged = (Map<String, Object>) value;
        @SuppressWarnings("unchecked")
        List<Object> adopted = (List<Object>) merged.get("adopted");
        assertEquals(1, adopted.size());
        @SuppressWarnings("unchecked")
        List<Object> rejected = (List<Object>) merged.get("rejected");
        assertEquals(List.of("item-2"), rejected);
        assertEquals(OPERATOR_ID.toString(), merged.get("operatorId"));
    }

    /** 从 wrapper 参数值里找到指定类型的第一个值（merged adoptResult 为 Map） */
    private static Object findParam(Object wrapper, Class<?> type) {
        for (Object value : ((LambdaUpdateWrapperX<?>) wrapper).getParamNameValuePairs().values()) {
            if (type.isInstance(value)) {
                return value;
            }
        }
        return null;
    }
}
