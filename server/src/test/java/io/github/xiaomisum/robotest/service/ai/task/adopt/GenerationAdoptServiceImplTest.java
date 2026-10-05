package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.tcase.ProjectModuleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.tcase.TestCaseDocumentCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiArtifactConfirm;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.ai.AiArtifactConfirmMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.ProjectModuleService;
import io.github.xiaomisum.robotest.service.domain.tcasedoc.TestCaseDocumentService;
import io.github.xiaomisum.robotest.service.trace.TraceEdgeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生成链产物采纳单测（C8）：权限、三类产物落库、疑似重复与父级未采纳防线、
 * 派生边与 editedDiff。
 */
@ExtendWith(MockitoExtension.class)
class GenerationAdoptServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_MODULE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private AiArtifactConfirmMapper confirmMapper;
    @Mock
    private ProjectModuleService projectModuleService;
    @Mock
    private TestCaseDocumentService testCaseDocumentService;
    @Mock
    private TraceEdgeWriter traceEdgeWriter;
    @Mock
    private VectorIndexService vectorIndexService;

    @InjectMocks
    private GenerationAdoptServiceImpl adopter;

    private AiTask task;

    @BeforeEach
    void setUp() {
        task = new AiTask();
        task.setId(TASK_ID);
        task.setProjectId(PROJECT_ID);
        task.setInput(new LinkedHashMap<>(Map.of(
                "requirementIds", List.of(REQUIREMENT_ID.toString()),
                "placement", "new_top_level")));
    }

    // ---------- 分派与权限 ----------

    @Test
    void adopt_rejected_returnsNull() {
        AdoptOutcome outcome = adopter.adopt(context(moduleArtifact(), "rejected", null));

        assertNull(outcome);
        verify(traceEdgeWriter, never()).writeAiDerivationEdges(any(), anyCollection(), anyString(), any(), anyString());
    }

    @Test
    void adopt_withoutCaseEditPermission_throws208() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("case:view"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(moduleArtifact(), "adopted", user)));
        assertEquals(ErrorCodeConstants.GENERATION_NO_ADOPT_PERMISSION.code(), exception.getCode());
    }

    @Test
    void adopt_unknownKind_throws115() {
        Map<String, Object> artifact = moduleArtifact();
        artifact.put("kind", "unknown_kind");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(artifact, "adopted", editor())));
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    // ---------- 模块 ----------

    @Test
    @SuppressWarnings("unchecked")
    void adoptModule_createsModuleAndWritesEdges() {
        ProjectModuleTreeRespDTO created = new ProjectModuleTreeRespDTO();
        created.setId(TARGET_MODULE_ID);
        when(projectModuleService.createModule(eq(PROJECT_ID), eq(OPERATOR_ID), any()))
                .thenReturn(created);

        AdoptOutcome outcome = adopter.adopt(context(moduleArtifact(), "adopted", editor()));

        assertNotNull(outcome);
        assertEquals(TARGET_MODULE_ID, outcome.createdId());
        assertEquals(String.valueOf(TARGET_MODULE_ID), outcome.adoptedRef().get("moduleId"));
        ArgumentCaptor<ProjectModuleCreateReqDTO> captor =
                ArgumentCaptor.forClass(ProjectModuleCreateReqDTO.class);
        verify(projectModuleService).createModule(eq(PROJECT_ID), eq(OPERATOR_ID), captor.capture());
        assertEquals("登录", captor.getValue().getName());
        assertNull(captor.getValue().getParentId());
        verify(traceEdgeWriter).writeAiDerivationEdges(eq(PROJECT_ID),
                eq(List.of(REQUIREMENT_ID)), eq("module"), eq(TARGET_MODULE_ID), isNull());
    }

    @Test
    void adoptModule_suspectedDuplicateAdoptedAsIs_throws204() {
        Map<String, Object> artifact = moduleArtifact();
        ((Map<String, Object>) artifact.get("content")).put("suspectedDuplicateOf",
                UUID.randomUUID().toString());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(artifact, "adopted", editor())));
        assertEquals(ErrorCodeConstants.GENERATION_ARTIFACT_DUPLICATE.code(), exception.getCode());
        verify(projectModuleService, never()).createModule(any(), any(), any());
    }

    @Test
    void adoptModule_targetModuleInvalid_throws203() {
        when(projectModuleMapper.selectById(TARGET_MODULE_ID)).thenReturn(null);
        Map<String, Object> artifact = moduleArtifact();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(artifact, "adopted", editor(), TARGET_MODULE_ID)));
        assertEquals(ErrorCodeConstants.GENERATION_TARGET_MODULE_INVALID.code(), exception.getCode());
    }

    // ---------- 脑图文档 ----------

    @Test
    void adoptDocument_resolvesModuleFromParentKeyAdoptedRef() {
        AiArtifactConfirm parentRecord = new AiArtifactConfirm();
        parentRecord.setAction("adopted");
        parentRecord.setAdoptedRef(Map.of("moduleId", String.valueOf(TARGET_MODULE_ID)));
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "module-1")).thenReturn(parentRecord);
        ProjectModule module = new ProjectModule();
        module.setId(TARGET_MODULE_ID);
        module.setProjectId(PROJECT_ID);
        when(projectModuleMapper.selectById(TARGET_MODULE_ID)).thenReturn(module);
        TestCaseDocumentRespDTO created = new TestCaseDocumentRespDTO();
        created.setId(DOCUMENT_ID);
        when(testCaseDocumentService.createTestCase(eq(PROJECT_ID), eq(OPERATOR_ID), any()))
                .thenReturn(created);

        AdoptOutcome outcome = adopter.adopt(context(documentArtifact(), "adopted", editor()));

        assertEquals(DOCUMENT_ID, outcome.createdId());
        assertEquals(String.valueOf(DOCUMENT_ID), outcome.adoptedRef().get("documentId"));
        verify(traceEdgeWriter).writeAiDerivationEdges(eq(PROJECT_ID),
                eq(List.of(REQUIREMENT_ID)), eq("mindmap_document"), eq(DOCUMENT_ID), isNull());
    }

    @Test
    void adoptDocument_parentModuleNotAdopted_throws20210() {
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "module-1")).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(documentArtifact(), "adopted", editor())));
        assertEquals(ErrorCodeConstants.GENERATION_PARENT_NOT_ADOPTED.code(), exception.getCode());
    }

    // ---------- 用例节点 ----------

    @Test
    @SuppressWarnings("unchecked")
    void adoptCaseNode_insertsChildrenAndWritesCaseEdge() {
        stubDocumentAdopted();
        TestCaseNode root = new TestCaseNode();
        root.setId(UUID.randomUUID());
        root.setParentId(null);
        when(testCaseNodeMapper.listByDocumentId(DOCUMENT_ID)).thenReturn(List.of(root));
        when(testCaseNodeMapper.listByParentId(root.getId())).thenReturn(List.of());
        doAnswer(invocation -> {
            TestCaseNode node = invocation.getArgument(0);
            node.setId(UUID.randomUUID());
            return 1;
        }).when(testCaseNodeMapper).insert(any(TestCaseNode.class));

        AdoptOutcome outcome = adopter.adopt(context(caseArtifact(), "adopted", editor()));

        ArgumentCaptor<TestCaseNode> captor = ArgumentCaptor.forClass(TestCaseNode.class);
        verify(testCaseNodeMapper, org.mockito.Mockito.times(6)).insert(captor.capture());
        List<TestCaseNode> inserted = captor.getAllValues();
        // 用例节点 + precondition + 2 step + 2 expected
        assertEquals("case", inserted.get(0).getType());
        assertEquals("P0", inserted.get(0).getPriority());
        assertEquals("precondition", inserted.get(1).getType());
        assertEquals("step", inserted.get(2).getType());
        assertEquals("expected", inserted.get(4).getType());
        assertEquals("caseId", outcome.adoptedRef().keySet().stream()
                .filter(key -> "caseId".equals(key)).findFirst().orElse(null));
        assertEquals(outcome.createdId(), outcome.adoptedRef().get("nodeId") == null ? null
                : UUID.fromString(String.valueOf(outcome.adoptedRef().get("nodeId"))));
        verify(traceEdgeWriter).writeAiDerivationEdges(eq(PROJECT_ID),
                eq(List.of(REQUIREMENT_ID)), eq("test_case"), eq(outcome.createdId()), eq("v1"));
        verify(vectorIndexService).upsertTestCase(DOCUMENT_ID, OPERATOR_ID);
    }

    @Test
    void adoptCaseNode_documentNotAdopted_throws20210() {
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "doc-1")).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(caseArtifact(), "adopted", editor())));
        assertEquals(ErrorCodeConstants.GENERATION_PARENT_NOT_ADOPTED.code(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void adoptEdited_writesEditedDiffIntoAdoptedRef() {
        ProjectModuleTreeRespDTO created = new ProjectModuleTreeRespDTO();
        created.setId(TARGET_MODULE_ID);
        when(projectModuleService.createModule(eq(PROJECT_ID), eq(OPERATOR_ID), any()))
                .thenReturn(created);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("name", "账户登录");
        AdoptOutcome outcome = adopter.adopt(context(moduleArtifact(), "adopted_edited", editor(), null, edit));

        Map<String, Object> diff = (Map<String, Object>) outcome.adoptedRef().get("editedDiff");
        assertNotNull(diff);
        assertEquals("登录", diff.get("name") == null ? null
                : ((Map<String, Object>) diff.get("name")).get("from"));
        assertEquals("账户登录", ((Map<String, Object>) diff.get("name")).get("to"));
    }

    // ---------- 辅助 ----------

    private void stubDocumentAdopted() {
        AiArtifactConfirm record = new AiArtifactConfirm();
        record.setAction("adopted");
        record.setAdoptedRef(Map.of("documentId", String.valueOf(DOCUMENT_ID)));
        when(confirmMapper.selectByTaskAndKey(TASK_ID, "doc-1")).thenReturn(record);
    }

    private static LoginUser editor() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("case:edit"));
        return user;
    }

    private AdoptContext context(Map<String, Object> artifact, String action, LoginUser user) {
        return context(artifact, action, user, null, null);
    }

    private AdoptContext context(Map<String, Object> artifact, String action, LoginUser user,
            UUID targetModuleId) {
        return context(artifact, action, user, targetModuleId, null);
    }

    private AdoptContext context(Map<String, Object> artifact, String action, LoginUser user,
            UUID targetModuleId, Map<String, Object> content) {
        return new AdoptContext(task, artifact, action, content, null, targetModuleId, null,
                null, null, PROJECT_ID, OPERATOR_ID, user);
    }

    private static Map<String, Object> moduleArtifact() {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "module-1");
        artifact.put("kind", "module_suggestion");
        artifact.put("title", "登录");
        artifact.put("content", new LinkedHashMap<String, Object>(Map.of(
                "name", "登录",
                "sourceRefs", List.of(Map.of("requirementId", REQUIREMENT_ID.toString(), "quote", "登录")))));
        return artifact;
    }

    private static Map<String, Object> documentArtifact() {
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "doc-1");
        artifact.put("kind", "mindmap_document_suggestion");
        artifact.put("parentKey", "module-1");
        artifact.put("title", "登录用例");
        artifact.put("content", new LinkedHashMap<String, Object>(Map.of(
                "name", "登录用例",
                "sourceRefs", List.of(Map.of("requirementId", REQUIREMENT_ID.toString(), "quote", "登录")))));
        return artifact;
    }

    private static Map<String, Object> caseArtifact() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("isTestCase", true);
        content.put("parentRef", null);
        content.put("title", "正确密码登录");
        content.put("attributes", new LinkedHashMap<String, Object>(Map.of(
                "priority", "high",
                "precondition", "已打开登录页",
                "steps", List.of("s1", "s2"),
                "expected", List.of("e1", "e2"),
                "tags", List.of("smoke"))));
        content.put("sourceRefs", List.of(Map.of("requirementId", REQUIREMENT_ID.toString(), "quote", "登录")));
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("key", "case-1");
        artifact.put("kind", "test_case_suggestion");
        artifact.put("parentKey", "doc-1");
        artifact.put("title", "正确密码登录");
        artifact.put("content", content);
        return artifact;
    }
}
