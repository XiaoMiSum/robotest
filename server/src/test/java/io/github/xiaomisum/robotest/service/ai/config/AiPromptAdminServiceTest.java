package io.github.xiaomisum.robotest.service.ai.config;

import io.github.xiaomisum.robotest.framework.audit.AuditLogWriter;
import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiPromptSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiPromptListItemRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiPromptTemplate;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.ai.AiPromptTemplateMapper;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPromptAdminServiceTest {

    private static final String SCENE = "requirement_import";
    private static final String BUILTIN_PROMPT = "基于 {{documentName}} {{documentText}} "
            + "{{moduleOptions}} 拆解导入";

    @Mock
    private AiPromptTemplateMapper promptMapper;
    @Mock
    private TaskTypeAdapterRegistry adapterRegistry;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AiPromptAdminService service;

    private LoginUser loginUser;

    @BeforeAll
    static void initTableInfo() {
        AiConfigTableInfo.init();
    }

    @BeforeEach
    void setUp() {
        loginUser = new LoginUser();
        loginUser.setId(UUID.randomUUID());
        loginUser.setUsername("admin");
        TaskTypeAdapter adapter = org.mockito.Mockito.mock(TaskTypeAdapter.class);
        lenient().when(adapter.systemPrompt()).thenReturn(BUILTIN_PROMPT);
        lenient().when(adapterRegistry.get(SCENE)).thenReturn(adapter);
        lenient().when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");
    }

    private static AiPromptSaveReqDTO saveReq(String content) {
        AiPromptSaveReqDTO req = new AiPromptSaveReqDTO();
        req.setContent(content);
        return req;
    }

    private static AiPromptTemplate customRow(String content, int version) {
        AiPromptTemplate row = new AiPromptTemplate();
        row.setId(UUID.randomUUID());
        row.setScene(SCENE);
        row.setName("需求导入");
        row.setContent(content);
        row.setSource("custom");
        row.setVersion(version);
        return row;
    }

    @Test
    void get_unregisteredScene_throwsSceneNotFound() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get("unknown_scene"));

        assertEquals(ErrorCodeConstants.AI_PROMPT_SCENE_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void get_noCustomRow_returnsBuiltinDefault() {
        when(promptMapper.selectOne(any())).thenReturn(null);

        AiPromptDetailRespDTO dto = service.get(SCENE);

        assertEquals("default", dto.getSource());
        assertEquals(0, dto.getVersion());
        assertEquals(BUILTIN_PROMPT, dto.getContent());
        assertEquals(3, dto.getVariables().size());
        assertTrue(dto.getVariables().stream().allMatch(v -> Boolean.TRUE.equals(v.getRequired())));
    }

    /** 回落链单轨（S4 收口）：任务场景正文取自 TypeAdapter，非任务场景取自 AiPromptScenes，无旧处理器注册表参与 */
    @Test
    void get_nonTaskScene_fallsBackToScenesBuiltinWithoutAdapter() {
        when(promptMapper.selectOne(any())).thenReturn(null);

        AiPromptDetailRespDTO dto = service.get("bug_draft_suggest");

        assertEquals("default", dto.getSource());
        assertEquals(AiPromptScenes.builtin("bug_draft_suggest"), dto.getContent());
    }

    @Test
    void save_unregisteredScene_throwsSceneNotFound() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save("unknown_scene", saveReq("x"), loginUser, request));

        assertEquals(ErrorCodeConstants.AI_PROMPT_SCENE_NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void save_undeclaredVariable_throwsPromptInvalid() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save(SCENE, saveReq("{{documentName}} {{documentText}} "
                        + "{{moduleOptions}} {{unknownVar}}"), loginUser, request));

        assertEquals(ErrorCodeConstants.AI_PROMPT_INVALID.code(), ex.getCode());
    }

    @Test
    void save_missingRequiredVariable_throwsPromptInvalid() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save(SCENE, saveReq("只有 {{documentName}}"), loginUser, request));

        assertEquals(ErrorCodeConstants.AI_PROMPT_INVALID.code(), ex.getCode());
    }

    @Test
    void save_newScene_insertsVersionOne() {
        when(promptMapper.selectOne(any())).thenReturn(null);

        service.save(SCENE, saveReq(BUILTIN_PROMPT), loginUser, request);

        ArgumentCaptor<AiPromptTemplate> captor = ArgumentCaptor.forClass(AiPromptTemplate.class);
        verify(promptMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
        assertEquals("custom", captor.getValue().getSource());
        assertEquals(loginUser.getId(), captor.getValue().getUpdatedBy());
    }

    @Test
    void save_existingRow_bumpsVersion() {
        AiPromptTemplate existing = customRow("旧内容", 3);
        when(promptMapper.selectOne(any())).thenReturn(existing);

        service.save(SCENE, saveReq(BUILTIN_PROMPT), loginUser, request);

        assertEquals(4, existing.getVersion());
        verify(promptMapper).updateById(existing);
    }

    @Test
    void reset_deletesCustomRow_andReturnsDefault() {
        AiPromptTemplate existing = customRow("旧内容", 3);
        // 首次读到自定义行，删除后回读 GET 再查为 null
        when(promptMapper.selectOne(any())).thenReturn(existing, null);

        AiPromptDetailRespDTO dto = service.reset(SCENE, loginUser, request);

        verify(promptMapper).deleteById(existing.getId());
        assertEquals("default", dto.getSource());
        assertEquals(BUILTIN_PROMPT, dto.getContent());
    }

    @Test
    void list_mergesRegistryAndCustomRows() {
        AiPromptTemplate existing = customRow(BUILTIN_PROMPT, 1);
        when(promptMapper.selectList(any())).thenReturn(List.of(existing));

        List<AiPromptListItemRespDTO> list = service.list();

        // 登记场景 13 个（生成链统一为 test_design_generation，不再登记四阶段旧场景），
        // 自定义行只覆盖导入
        assertEquals(13, list.size());
        AiPromptListItemRespDTO importItem = list.stream()
            .filter(item -> "requirement_import".equals(item.getScene()))
                .findFirst().orElseThrow();
        assertEquals("custom", importItem.getSource());
        assertEquals("需求导入", importItem.getName());
        assertNull(importItem.getUpdatedByName());
        assertTrue(importItem.getSummary().contains("基于"));
        AiPromptListItemRespDTO defaultItem = list.stream()
                .filter(item -> "test_design_generation".equals(item.getScene()))
                .findFirst().orElseThrow();
        assertEquals("default", defaultItem.getSource());
        assertEquals("测试设计生成", defaultItem.getName());
    }

    @Test
    void list_noCustomRows_usesBuiltinSummary() {
        when(promptMapper.selectList(any())).thenReturn(List.of());

        List<AiPromptListItemRespDTO> list = service.list();

        assertEquals(13, list.size());
        AiPromptListItemRespDTO importItem = list.stream()
                .filter(item -> "requirement_import".equals(item.getScene()))
                .findFirst().orElseThrow();
        assertEquals("default", importItem.getSource());
        assertNull(importItem.getUpdatedAt());
    }
}
