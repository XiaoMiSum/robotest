package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugDraftSuggestReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiVectorSearchHitRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.vector.VectorIndexService;
import io.github.xiaomisum.robotest.service.ai.vector.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugDraftSuggestServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MODULE_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @Mock
    private ProjectModuleMapper projectModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private AiSettingsReader settingsReader;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AiPromptService promptService;
    @Mock
    private BugSuggestStreamBridge streamBridge;
    @Mock
    private BugSuggestStreamRunner streamRunner;
    @Mock
    private BugSuggestStreamBridge.StreamChannel channel;

    @InjectMocks
    private BugDraftSuggestServiceImpl service;

    private BugDraftSuggestReqDTO request(String title, String steps, String moduleId) {
        BugDraftSuggestReqDTO reqDTO = new BugDraftSuggestReqDTO();
        reqDTO.setTitle(title);
        reqDTO.setSteps(steps);
        reqDTO.setModuleId(moduleId);
        return reqDTO;
    }

    private ProjectModule module() {
        ProjectModule module = new ProjectModule();
        module.setId(MODULE_ID);
        module.setName("登录模块");
        return module;
    }

    private TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(CASE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setType("case");
        node.setTitle("登录-正常登录");
        return node;
    }

    private AiVectorSearchHitRespDTO hit(UUID entityId) {
        AiVectorSearchHitRespDTO hit = new AiVectorSearchHitRespDTO();
        hit.setEntityId(entityId);
        hit.setEntityType(VectorIndexService.TYPE_TESTCASE);
        hit.setProjectId(PROJECT_ID);
        return hit;
    }

    private void stubStream(AiChatReply reply) {
        when(promptService.render(any(), any(), any())).thenReturn("prompt");
        when(modelClient.streamChat(any(), any())).thenReturn(reply);
    }

    /** 记录事件序的 sink：stream() 只经 sink 回写，可直接断言 progress / done / error 契约 */
    private static final class RecordingSink implements BugDraftSuggestService.SuggestStreamSink {

        private final List<String> events = new ArrayList<>();
        private final List<String> stages = new ArrayList<>();
        private BugDraftSuggestRespDTO resp;
        private int errorCode;
        private String errorMessage;

        @Override
        public void progress(String stage) {
            events.add("progress");
            stages.add(stage);
        }

        @Override
        public void done(BugDraftSuggestRespDTO resp) {
            events.add("done");
            this.resp = resp;
        }

        @Override
        public void error(int code, String message) {
            events.add("error");
            this.errorCode = code;
            this.errorMessage = message;
        }
    }

    // ---------- 前置校验（同步 HTTP 错误，非流） ----------

    @Test
    void suggest_emptyTitle_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.suggest(request("  ", null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_SUGGEST_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void suggest_overlongTitle_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.suggest(request("标".repeat(301), null, null), PROJECT_ID, USER_ID));
        assertEquals(ErrorCodeConstants.BUG_ANALYSIS_SUGGEST_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void suggest_noUsableModel_throwsNotConfigured() {
        when(settingsReader.resolveModel(null)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.suggest(request("登录无响应", null, null), PROJECT_ID, USER_ID));

        assertEquals(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED.code(), exception.getCode());
    }

    @Test
    void suggest_validInput_attachesChannelAndStartsStreamAsync() {
        when(settingsReader.resolveModel(null)).thenReturn(new AiModelConfig());
        when(streamBridge.attach(any(SseEmitter.class))).thenReturn(channel);

        SseEmitter emitter = service.suggest(request("登录无响应", null, null), PROJECT_ID, USER_ID);

        assertNotNull(emitter);
        verify(streamBridge).attach(any(SseEmitter.class));
        verify(streamRunner).run(any(Runnable.class));
    }

    // ---------- 流式装配与清洗 ----------

    @Test
    void stream_sanitizesSuggestionsAndExpandsCaseCandidates() {
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(vectorSearchService.search(eq("登录页点击登录无响应"), anyList(),
                eq(VectorIndexService.TYPE_TESTCASE), anyInt(), eq(USER_ID)))
                .thenReturn(List.of(hit(DOCUMENT_ID)));
        when(testCaseNodeMapper.listCasesByDocumentIds(anyCollection())).thenReturn(List.of(caseNode()));
        stubStream(new AiChatReply("""
                {"title":{"value":"登录页点击登录按钮后无任何响应","reason":"现象明确"},
                 "reproSteps":{"value":"##### 执行步骤\\n1. 打开登录页\\n##### 实际结果\\n无响应\\n##### 期望结果\\n登录成功","reason":"三段结构"},
                 "suggestions":{
                   "bugType":{"value":"code_error","reason":"功能失效"},
                   "severity":{"value":"blocker","reason":"非法枚举应剔除"},
                   "priority":{"value":"high","reason":"主流程"},
                   "moduleId":{"value":"%s","reason":"登录相关"},
                   "keywords":{"value":["登录","无响应","按钮"],"reason":"检索词"},
                   "relatedCase":{"value":"%s","caseTitle":"登录-正常登录","reason":"正向用例"}}}
                """.formatted(MODULE_ID, CASE_ID), 10, 5));
        RecordingSink sink = new RecordingSink();

        service.stream(request("登录页点击登录无响应", "1. 打开登录页", MODULE_ID.toString()),
                PROJECT_ID, USER_ID, new AiModelConfig(), sink);

        // 事件序：装配与生成各一个 progress，终态 done，无 error
        assertEquals(List.of("progress", "progress", "done"), sink.events);
        assertEquals(List.of("context", "generating"), sink.stages);

        BugDraftSuggestRespDTO resp = sink.resp;
        assertEquals("登录页点击登录按钮后无任何响应", resp.getTitle().getValue());
        assertTrue(resp.getReproSteps().getValue().contains("##### 期望结果"));
        assertEquals("code_error", resp.getSuggestions().getBugType().getValue());
        // 非法枚举剔除，合法模块 / 用例保留
        assertNull(resp.getSuggestions().getSeverity());
        assertEquals(MODULE_ID.toString(), resp.getSuggestions().getModuleId().getValue());
        assertEquals(List.of("登录", "无响应", "按钮"), resp.getSuggestions().getKeywords().getValue());
        assertEquals(CASE_ID.toString(), resp.getSuggestions().getRelatedCase().getValue());
        assertEquals("登录-正常登录", resp.getSuggestions().getRelatedCase().getCaseTitle());

        // 上下文变量：已选模块名称与候选用例清单均进入提示词
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> varCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptService).render(any(), any(), varCaptor.capture());
        assertTrue(varCaptor.getValue().get("bugContext").contains("已选模块：登录模块"));
        assertTrue(varCaptor.getValue().get("caseOptions").contains(CASE_ID + "|登录-正常登录"));
    }

    @Test
    void stream_relatedCaseOutsideCandidates_isDropped() {
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        stubStream(new AiChatReply("""
                {"suggestions":{"relatedCase":{"value":"%s","caseTitle":"臆造用例","reason":"不在候选"}}}
                """.formatted(UUID.randomUUID()), 10, 5));
        RecordingSink sink = new RecordingSink();

        service.stream(request("登录无响应", null, null), PROJECT_ID, USER_ID, new AiModelConfig(), sink);

        // 向量未就绪 → 无用例候选 → relatedCase 一律剔除
        assertNull(sink.resp.getSuggestions().getRelatedCase());
        assertEquals("done", sink.events.get(sink.events.size() - 1));
    }

    // ---------- 向量未就绪降级 ----------

    @Test
    void stream_vectorUnavailable_degradesToNoCase() {
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of(module()));
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_VECTOR_INDEX_UNAVAILABLE));
        stubStream(new AiChatReply(
                "{\"title\":{\"value\":\"优化后标题\",\"reason\":\"\"},\"suggestions\":{}}", 10, 5));
        RecordingSink sink = new RecordingSink();

        service.stream(request("登录无响应", null, null), PROJECT_ID, USER_ID, new AiModelConfig(), sink);

        assertEquals("优化后标题", sink.resp.getTitle().getValue());
        assertNull(sink.resp.getSuggestions().getRelatedCase());
    }

    // ---------- 流内失败（error 事件） ----------

    @Test
    void stream_modelCallFailed_emitsErrorEvent() {
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(vectorSearchService.search(any(), anyList(), any(), anyInt(), any())).thenReturn(List.of());
        when(promptService.render(any(), any(), any())).thenReturn("prompt");
        when(modelClient.streamChat(any(), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));
        RecordingSink sink = new RecordingSink();

        service.stream(request("登录无响应", null, null), PROJECT_ID, USER_ID, new AiModelConfig(), sink);

        assertEquals(List.of("progress", "progress", "error"), sink.events);
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), sink.errorCode);
        assertNull(sink.resp);
    }

    @Test
    void stream_assemblyFailure_mapsToModelErrorEvent() {
        when(projectModuleMapper.listByProjectId(PROJECT_ID)).thenThrow(new RuntimeException("db down"));
        RecordingSink sink = new RecordingSink();

        service.stream(request("登录无响应", null, null), PROJECT_ID, USER_ID, new AiModelConfig(), sink);

        assertEquals(List.of("progress", "error"), sink.events);
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), sink.errorCode);
    }
}
