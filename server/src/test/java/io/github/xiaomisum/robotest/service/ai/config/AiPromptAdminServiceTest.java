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
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandlerRegistry;
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

    private static final String SCENE = "requirement_split";
    private static final String BUILTIN_PROMPT = "基于 {{requirementCode}} {{requirementTitle}} "
            + "{{requirementDescription}} {{moduleOptions}} 拆分";

    @Mock
    private AiPromptTemplateMapper promptMapper;
    @Mock
    private TaskHandlerRegistry handlerRegistry;
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
        TaskHandler handler = org.mockito.Mockito.mock(TaskHandler.class);
        lenient().when(handler.defaultPrompt()).thenReturn(BUILTIN_PROMPT);
        lenient().when(handlerRegistry.get(SCENE)).thenReturn(handler);
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
        row.setName("需求拆分");
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
        assertEquals(4, dto.getVariables().size());
        assertTrue(dto.getVariables().stream().allMatch(v -> Boolean.TRUE.equals(v.getRequired())));
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
                () -> service.save(SCENE, saveReq("{{requirementCode}} {{requirementTitle}} "
                        + "{{requirementDescription}} {{moduleOptions}} {{unknownVar}}"), loginUser, request));

        assertEquals(ErrorCodeConstants.AI_PROMPT_INVALID.code(), ex.getCode());
    }

    @Test
    void save_missingRequiredVariable_throwsPromptInvalid() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.save(SCENE, saveReq("只有 {{requirementCode}}"), loginUser, request));

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

        assertEquals(1, list.size());
        assertEquals("custom", list.get(0).getSource());
        assertEquals("需求拆分", list.get(0).getName());
        assertNull(list.get(0).getUpdatedByName());
        assertTrue(list.get(0).getSummary().contains("基于"));
    }

    @Test
    void list_noCustomRows_usesBuiltinSummary() {
        when(promptMapper.selectList(any())).thenReturn(List.of());

        List<AiPromptListItemRespDTO> list = service.list();

        assertEquals(1, list.size());
        assertEquals("default", list.get(0).getSource());
        assertNull(list.get(0).getUpdatedAt());
    }
}
