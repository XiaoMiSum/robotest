package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantEntityRefReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessageSendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationStatRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantMessageRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceImplTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final UUID USER_MESSAGE_ID = UUID.randomUUID();
    private static final UUID ASSISTANT_MESSAGE_ID = UUID.randomUUID();

    @Mock
    private AiAssistantConversationMapper conversationMapper;
    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private AssistantRefChecker refChecker;
    @Mock
    private AssistantStreamBridge streamBridge;
    @Mock
    private AiTaskService aiTaskService;

    @InjectMocks
    private AssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        // 纯单测无 MP 主键填充：插入桩补写 id，使创建响应可回传；仅创建用例覆盖，故 lenient
        lenient().doAnswer(invocation -> {
            AiAssistantConversation entity = invocation.getArgument(0);
            entity.setId(CONVERSATION_ID);
            return 1;
        }).when(conversationMapper).insert(any(AiAssistantConversation.class));
        // 发送落盘两条消息补写主键，使 attach / submit 能定位 assistant 消息
        lenient().doAnswer(invocation -> {
            AiAssistantMessage message = invocation.getArgument(0);
            message.setId(Constants.AiAssistantMessageRole.USER.equals(message.getRole())
                    ? USER_MESSAGE_ID : ASSISTANT_MESSAGE_ID);
            return 1;
        }).when(messageMapper).insert(any(AiAssistantMessage.class));
    }

    @Test
    void pageConversations_returnsEmpty_whenNoConversations() {
        AiAssistantConversationPageReqDTO pageReq = new AiAssistantConversationPageReqDTO();
        when(conversationMapper.findPage(eq(pageReq), eq(USER_ID), isNull()))
                .thenReturn(new PageResult<>(List.of(), 0L));

        PageResult<AiAssistantConversationRespDTO> result = service.pageConversations(pageReq, USER_ID);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void pageConversations_mergesStatsPerConversation() {
        AiAssistantConversation first = conversation("第一个会话");
        AiAssistantConversation second = conversation("第二个会话");
        second.setId(UUID.randomUUID());
        AiAssistantConversationPageReqDTO pageReq = new AiAssistantConversationPageReqDTO();
        when(conversationMapper.findPage(eq(pageReq), eq(USER_ID), isNull()))
                .thenReturn(new PageResult<>(List.of(first, second), 2L));
        AiAssistantConversationStatRowDTO stat = new AiAssistantConversationStatRowDTO();
        stat.setConversationId(first.getId());
        stat.setMessageCount(3);
        stat.setLastMessageAt(LocalDateTime.now());
        when(messageMapper.statByConversationIds(anyCollection())).thenReturn(List.of(stat));

        PageResult<AiAssistantConversationRespDTO> result = service.pageConversations(pageReq, USER_ID);

        assertEquals(2, result.getList().size());
        AiAssistantConversationRespDTO merged = result.getList().get(0);
        assertEquals(CONVERSATION_ID, merged.getId());
        assertEquals(3, merged.getMessageCount());
        assertNotNull(merged.getLastMessageAt());
        // 无消息行的会话回落到 0，不按 null 输出
        assertEquals(0, result.getList().get(1).getMessageCount());
        assertNull(result.getList().get(1).getLastMessageAt());
    }

    @Test
    void createConversation_defaultsTitleAndBuildsSnapshot() {
        AiAssistantConversationCreateReqDTO reqDTO = new AiAssistantConversationCreateReqDTO();

        AiAssistantConversationRespDTO resp = service.createConversation(reqDTO, USER_ID, WORKSPACE_ID);

        ArgumentCaptor<AiAssistantConversation> captor = ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).insert(captor.capture());
        AiAssistantConversation saved = captor.getValue();
        assertEquals("新会话", saved.getTitle());
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(Constants.Status.ACTIVE, saved.getStatus());
        assertEquals(CONVERSATION_ID, resp.getId());
        assertEquals(WORKSPACE_ID, saved.getContextSnapshot().get("workspaceId"));
        assertNotNull(saved.getContextSnapshot().get("capturedAt"));
        verifyNoInteractions(refChecker);
    }

    @Test
    void createConversation_keepsProvidedTitle() {
        AiAssistantConversationCreateReqDTO reqDTO = new AiAssistantConversationCreateReqDTO();
        reqDTO.setTitle("  登录模块用例  ");

        service.createConversation(reqDTO, USER_ID, WORKSPACE_ID);

        ArgumentCaptor<AiAssistantConversation> captor = ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).insert(captor.capture());
        assertEquals("登录模块用例", captor.getValue().getTitle());
    }

    @Test
    void createConversation_validatesContextRefVisibility() {
        AiAssistantConversationCreateReqDTO reqDTO = new AiAssistantConversationCreateReqDTO();
        AiAssistantEntityRefReqDTO context = new AiAssistantEntityRefReqDTO();
        context.setEntityType(Constants.TraceNodeType.MINDMAP_DOCUMENT);
        context.setEntityId(ENTITY_ID);
        reqDTO.setContext(context);

        service.createConversation(reqDTO, USER_ID, WORKSPACE_ID);

        verify(refChecker).requireUserVisible(Constants.TraceNodeType.MINDMAP_DOCUMENT, ENTITY_ID, USER_ID);
    }

    @Test
    void createConversation_skipsRefValidation_whenEntityIdAbsent() {
        AiAssistantConversationCreateReqDTO reqDTO = new AiAssistantConversationCreateReqDTO();
        AiAssistantEntityRefReqDTO context = new AiAssistantEntityRefReqDTO();
        context.setEntityType(Constants.TraceNodeType.MINDMAP_DOCUMENT);
        reqDTO.setContext(context);

        service.createConversation(reqDTO, USER_ID, WORKSPACE_ID);

        verifyNoInteractions(refChecker);
    }

    @Test
    void createConversation_rejectsInvisibleRef_andSkipsInsert() {
        AiAssistantConversationCreateReqDTO reqDTO = new AiAssistantConversationCreateReqDTO();
        AiAssistantEntityRefReqDTO context = new AiAssistantEntityRefReqDTO();
        context.setEntityType(Constants.TraceNodeType.TEST_PLAN);
        context.setEntityId(ENTITY_ID);
        reqDTO.setContext(context);
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID))
                .when(refChecker).requireUserVisible(any(), any(), any());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createConversation(reqDTO, USER_ID, WORKSPACE_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), exception.getCode());
        verify(conversationMapper, never()).insert(any(AiAssistantConversation.class));
    }

    @Test
    void getOwnedConversation_returnsOwnerRow() {
        AiAssistantConversation owned = conversation("本人会话");
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(owned);

        AiAssistantConversation result = service.getOwnedConversation(CONVERSATION_ID, USER_ID);

        assertEquals(CONVERSATION_ID, result.getId());
    }

    @Test
    void getOwnedConversation_rejectsForeignAndMissingWithSameCode() {
        AiAssistantConversation foreign = conversation("他人会话");
        foreign.setUserId(OTHER_ID);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(foreign);

        ServiceException foreignError = assertThrows(ServiceException.class,
                () -> service.getOwnedConversation(CONVERSATION_ID, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), foreignError.getCode());

        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(null);
        ServiceException missingError = assertThrows(ServiceException.class,
                () -> service.getOwnedConversation(CONVERSATION_ID, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), missingError.getCode());
    }

    @Test
    void updateConversation_requiresAtLeastOneField() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.updateConversation(
                CONVERSATION_ID, new AiAssistantConversationUpdateReqDTO(), USER_ID));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void updateConversation_rejectsBlankTitle() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        AiAssistantConversationUpdateReqDTO reqDTO = new AiAssistantConversationUpdateReqDTO();
        reqDTO.setTitle("   ");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateConversation(CONVERSATION_ID, reqDTO, USER_ID));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void updateConversation_rejectsStatusOtherThanArchived() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        AiAssistantConversationUpdateReqDTO reqDTO = new AiAssistantConversationUpdateReqDTO();
        reqDTO.setStatus(Constants.Status.ACTIVE);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateConversation(CONVERSATION_ID, reqDTO, USER_ID));

        assertEquals(ErrorCodeConstants.VALIDATION_FAILED.code(), exception.getCode());
    }

    @Test
    void updateConversation_rejectsForeignConversation() {
        AiAssistantConversation foreign = conversation("他人会话");
        foreign.setUserId(OTHER_ID);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(foreign);
        AiAssistantConversationUpdateReqDTO reqDTO = new AiAssistantConversationUpdateReqDTO();
        reqDTO.setTitle("新标题");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.updateConversation(CONVERSATION_ID, reqDTO, USER_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), exception.getCode());
        verify(conversationMapper, never()).updateById(any(AiAssistantConversation.class));
    }

    @Test
    void updateConversation_rename_carriesOnlyChangedField() {
        AiAssistantConversation owned = conversation("旧标题");
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(owned);
        AiAssistantConversationUpdateReqDTO reqDTO = new AiAssistantConversationUpdateReqDTO();
        reqDTO.setTitle("新标题");

        AiAssistantConversationRespDTO resp = service.updateConversation(CONVERSATION_ID, reqDTO, USER_ID);

        ArgumentCaptor<AiAssistantConversation> captor = ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).updateById(captor.capture());
        AiAssistantConversation carrier = captor.getValue();
        assertEquals(CONVERSATION_ID, carrier.getId());
        assertEquals("新标题", carrier.getTitle());
        // C11：未传字段不得进入更新载体
        assertNull(carrier.getStatus());
        assertNull(carrier.getUserId());
        assertNull(carrier.getContextSnapshot());
        assertEquals(CONVERSATION_ID, resp.getId());
    }

    @Test
    void updateConversation_archive_carriesOnlyStatus() {
        AiAssistantConversation owned = conversation("会话");
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(owned);
        AiAssistantConversationUpdateReqDTO reqDTO = new AiAssistantConversationUpdateReqDTO();
        reqDTO.setStatus(Constants.Status.ARCHIVED);

        service.updateConversation(CONVERSATION_ID, reqDTO, USER_ID);

        ArgumentCaptor<AiAssistantConversation> captor = ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).updateById(captor.capture());
        assertEquals(Constants.Status.ARCHIVED, captor.getValue().getStatus());
        assertNull(captor.getValue().getTitle());
    }

    @Test
    void deleteConversation_removesConversationAndMessages() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));

        boolean deleted = service.deleteConversation(CONVERSATION_ID, USER_ID);

        assertTrue(deleted);
        verify(messageMapper).delete(any());
        verify(conversationMapper).deleteById(CONVERSATION_ID);
    }

    @Test
    void deleteConversation_rejectsForeignConversation() {
        AiAssistantConversation foreign = conversation("他人会话");
        foreign.setUserId(OTHER_ID);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(foreign);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.deleteConversation(CONVERSATION_ID, USER_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), exception.getCode());
        verify(conversationMapper, never()).deleteById(any(UUID.class));
        verify(messageMapper, never()).delete(any());
    }

    @Test
    void pageMessages_rejectsForeignConversationBeforePaging() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.pageMessages(
                CONVERSATION_ID, new AiAssistantMessagePageReqDTO(), USER_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND.code(), exception.getCode());
        verify(messageMapper, never()).findPage(any(PageParam.class), any());
    }

    @Test
    void pageMessages_mapsMessageFields() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(CONVERSATION_ID);
        message.setRole("assistant");
        message.setContent("回答正文");
        message.setAttachments(List.of(Map.of("entityType", "mindmap_document", "entityId", ENTITY_ID.toString())));
        message.setCitations(List.of(Map.of("entityId", ENTITY_ID.toString())));
        message.setIntent(Map.of("action", "create_case"));
        message.setExecution(Map.of("status", "executed"));
        message.setStatus("done");
        message.setCreatedAt(LocalDateTime.now());
        AiAssistantMessagePageReqDTO pageReq = new AiAssistantMessagePageReqDTO();
        when(messageMapper.findPage(pageReq, CONVERSATION_ID))
                .thenReturn(new PageResult<>(List.of(message), 1L));

        PageResult<AiAssistantMessageRespDTO> result = service.pageMessages(CONVERSATION_ID, pageReq, USER_ID);

        assertEquals(1, result.getTotal());
        AiAssistantMessageRespDTO resp = result.getList().get(0);
        assertEquals(message.getId(), resp.getId());
        assertEquals(CONVERSATION_ID, resp.getConversationId());
        assertEquals("assistant", resp.getRole());
        assertEquals("回答正文", resp.getContent());
        assertEquals("done", resp.getStatus());
        assertEquals("create_case", resp.getIntent().get("action"));
        assertEquals("executed", resp.getExecution().get("status"));
        assertEquals(1, resp.getCitations().size());
        assertEquals(1, resp.getAttachments().size());
        assertNotNull(resp.getCreatedAt());
    }

    // ---------- 发送消息（详设 3.5） ----------

    @Test
    @SuppressWarnings("unchecked")
    void send_persistsBothMessagesAttachesAndSubmits() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("新会话"));
        when(messageMapper.statByConversationIds(anyCollection())).thenReturn(List.of());
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("  分析登录模块的用例覆盖  ");

        SseEmitter emitter = service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID);

        assertNotNull(emitter);
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        AiAssistantMessage userMessage = captor.getAllValues().get(0);
        assertEquals(Constants.AiAssistantMessageRole.USER, userMessage.getRole());
        assertEquals("分析登录模块的用例覆盖", userMessage.getContent());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, userMessage.getStatus());
        AiAssistantMessage assistantMessage = captor.getAllValues().get(1);
        assertEquals(Constants.AiAssistantMessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(Constants.AiAssistantMessageStatus.STREAMING, assistantMessage.getStatus());
        // 首问回填标题（30 字内取全文，只携带 id + title，C11）
        ArgumentCaptor<AiAssistantConversation> renameCaptor =
                ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).updateById(renameCaptor.capture());
        assertEquals("分析登录模块的用例覆盖", renameCaptor.getValue().getTitle());
        assertEquals(CONVERSATION_ID, renameCaptor.getValue().getId());
        verify(streamBridge).attach(eq(ASSISTANT_MESSAGE_ID), any(SseEmitter.class));
        verify(refChecker).requireWorkspaceMember(WORKSPACE_ID, USER_ID);

        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiTaskService).submitInternal(eq("assistant_parse"), inputCaptor.capture(),
                eq(USER_ID), isNull(), eq(WORKSPACE_ID));
        assertEquals(ASSISTANT_MESSAGE_ID.toString(), inputCaptor.getValue().get("messageId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void send_skipsTitleBackfill_whenMessageCountNonZero() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("新会话"));
        AiAssistantConversationStatRowDTO stat = new AiAssistantConversationStatRowDTO();
        stat.setConversationId(CONVERSATION_ID);
        stat.setMessageCount(5);
        when(messageMapper.statByConversationIds(anyCollection())).thenReturn(List.of(stat));
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("再来一个问题");

        service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID);

        verify(conversationMapper, never()).updateById(any(AiAssistantConversation.class));
        verify(messageMapper, times(2)).insert(any(AiAssistantMessage.class));
    }

    @Test
    void send_skipsTitleBackfill_whenUserRenamedConversation() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("自定义标题"));
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("再来一个问题");

        service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID);

        verify(conversationMapper, never()).updateById(any(AiAssistantConversation.class));
    }

    @Test
    void send_rejectsArchivedConversation() {
        AiAssistantConversation archived = conversation("归档会话");
        archived.setStatus(Constants.Status.ARCHIVED);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(archived);
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("任意内容");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_CONVERSATION_ARCHIVED.code(), exception.getCode());
        verify(messageMapper, never()).insert(any(AiAssistantMessage.class));
    }

    @Test
    void send_rejectsBlankOrOverlongContent() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));

        AiAssistantMessageSendReqDTO blank = new AiAssistantMessageSendReqDTO();
        blank.setContent("   ");
        ServiceException blankError = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, blank, USER_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CONTENT_INVALID.code(), blankError.getCode());

        AiAssistantMessageSendReqDTO overlong = new AiAssistantMessageSendReqDTO();
        overlong.setContent("a".repeat(4001));
        ServiceException overlongError = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, overlong, USER_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CONTENT_INVALID.code(), overlongError.getCode());
        verify(messageMapper, never()).insert(any(AiAssistantMessage.class));
    }

    @Test
    void send_requiresWorkspaceHeader() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("任意内容");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, reqDTO, USER_ID, null));

        assertEquals(ErrorCodeConstants.CONTEXT_HEADER_MISSING.code(), exception.getCode());
        verifyNoInteractions(refChecker);
    }

    @Test
    void send_rejectsNonMember() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE))
                .when(refChecker).requireWorkspaceMember(WORKSPACE_ID, USER_ID);
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("任意内容");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), exception.getCode());
        verify(messageMapper, never()).insert(any(AiAssistantMessage.class));
    }

    @Test
    void send_rejectsIncompleteOrForeignAttachment() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        AiAssistantMessageSendReqDTO incomplete = new AiAssistantMessageSendReqDTO();
        incomplete.setContent("带附件");
        AiAssistantEntityRefReqDTO missingId = new AiAssistantEntityRefReqDTO();
        missingId.setEntityType(Constants.TraceNodeType.MINDMAP_DOCUMENT);
        incomplete.setAttachments(List.of(missingId));

        ServiceException incompleteError = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, incomplete, USER_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), incompleteError.getCode());

        AiAssistantMessageSendReqDTO foreign = new AiAssistantMessageSendReqDTO();
        foreign.setContent("带附件");
        AiAssistantEntityRefReqDTO ref = new AiAssistantEntityRefReqDTO();
        ref.setEntityType(Constants.TraceNodeType.MINDMAP_DOCUMENT);
        ref.setEntityId(ENTITY_ID);
        foreign.setAttachments(List.of(ref));
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID))
                .when(refChecker).requireInActiveWorkspace(
                        Constants.TraceNodeType.MINDMAP_DOCUMENT, ENTITY_ID, WORKSPACE_ID);

        ServiceException foreignError = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, foreign, USER_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), foreignError.getCode());
        verify(messageMapper, never()).insert(any(AiAssistantMessage.class));
    }

    @Test
    void send_detachesChannelWhenSubmitFails() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation("会话"));
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED))
                .when(aiTaskService).submitInternal(anyString(), anyMap(), any(), any(), any());
        AiAssistantMessageSendReqDTO reqDTO = new AiAssistantMessageSendReqDTO();
        reqDTO.setContent("任意内容");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.send(CONVERSATION_ID, reqDTO, USER_ID, WORKSPACE_ID));

        assertEquals(ErrorCodeConstants.AI_TASK_TYPE_UNSUPPORTED.code(), exception.getCode());
        // 提交失败回收通道注册，事务回滚两条消息
        verify(streamBridge).detach(ASSISTANT_MESSAGE_ID);
    }

    private AiAssistantConversation conversation(String title) {
        AiAssistantConversation entity = new AiAssistantConversation();
        entity.setId(CONVERSATION_ID);
        entity.setTitle(title);
        entity.setUserId(USER_ID);
        entity.setStatus(Constants.Status.ACTIVE);
        return entity;
    }
}
