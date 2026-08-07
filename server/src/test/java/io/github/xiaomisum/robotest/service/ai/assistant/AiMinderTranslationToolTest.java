package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiStatusRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.AiChatModelService;
import io.github.xiaomisum.robotest.service.ai.AiConfigService;
import io.github.xiaomisum.robotest.service.ai.model.AiModels;
import io.github.xiaomisum.robotest.service.ai.provider.OpenAiCompatProvider;
import io.github.xiaomisum.robotest.service.ai.provider.PromptAssembler;
import io.github.xiaomisum.robotest.service.ai.provider.ResolvedChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMinderTranslationToolTest {

    @Mock
    private PromptAssembler promptAssembler;
    @Mock
    private OpenAiCompatProvider provider;
    @Mock
    private AiChatModelService chatModelService;
    @Mock
    private AiConfigService configService;
    @Mock
    private TestCaseModuleMapper testCaseModuleMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    private final JsonMapper objectMapper = new JsonMapper();

    private AiMinderTranslationTool tool;
    private AiToolContext context;
    private UUID documentId;
    private ResolvedChatModel model;

    @BeforeEach
    void setUp() {
        tool = new AiMinderTranslationTool(promptAssembler, provider, chatModelService,
                configService, testCaseModuleMapper, testCaseNodeMapper, objectMapper);
        documentId = UUID.randomUUID();
        context = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(),
                Map.of("documentId", documentId.toString()));
        model = new ResolvedChatModel(UUID.randomUUID(), "gpt", "openai",
                "http://localhost", "key", "gpt-4o", Map.of());
    }

    @Test
    void definition_returnsNameAndReadOnly() {
        AiToolDefinition def = tool.definition();

        assertEquals("translate_minder_command", def.name());
        assertTrue(def.readOnly());
    }

    @Test
    void execute_translatesAndBuildsSummary() {
        stubEnabledChatEnvironment(List.of());
        when(provider.complete(eq(model), anyList(), any())).thenReturn(new AiModels.ChatResult(
                "{\"commands\":[{\"action\":{\"type\":\"add_child\",\"params\":{}}},"
                        + "{\"action\":\"mark_type\"},{\"action\":123}],"
                        + "\"ambiguous\":false,\"clarification\":null}",
                10, 5, "stop"));

        String result = tool.execute(context, Map.of("instruction", "给登录模块新增一个子节点"));

        assertTrue(result.contains("\"summary\""));
        assertTrue(result.contains("新增节点 ×1"));
        assertTrue(result.contains("标记类型 ×1"));
        assertTrue(result.contains("未知操作 ×1"));
        assertTrue(result.contains(documentId.toString()));
        assertTrue(result.contains("add_child"));
        verify(provider).complete(eq(model), anyList(),
                argThat(opt -> opt.temperature() == 0.1 && opt.jsonResponseFormat()));
    }

    @Test
    void execute_missingInstructionReturnsError() {
        String result = tool.execute(context, Map.of());

        assertTrue(result.contains("缺少 instruction 参数"));
        verify(testCaseModuleMapper, never()).selectById(any());
    }

    @Test
    void execute_missingDocumentContextReturnsError() {
        AiToolContext noDocCtx = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

        String result = tool.execute(noDocCtx, Map.of("instruction", "新增节点"));

        assertTrue(result.contains("缺少文档上下文 documentId"));
        verify(testCaseModuleMapper, never()).selectById(any());
    }

    @Test
    void execute_documentNotFoundReturnsError() {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(null);

        String result = tool.execute(context, Map.of("instruction", "新增节点"));

        assertTrue(result.contains("文档不存在或已被删除"));
        verify(configService, never()).getStatus();
    }

    @Test
    void execute_aiDisabledReturnsError() {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(document());
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(false);
        when(configService.getStatus()).thenReturn(status);

        String result = tool.execute(context, Map.of("instruction", "新增节点"));

        assertTrue(result.contains(ErrorCodeConstants.AI_NOT_ENABLED.msg()));
        verify(chatModelService, never()).resolve(any());
    }

    @Test
    void execute_modelUnresolvedReturnsError() {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(document());
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(true);
        when(configService.getStatus()).thenReturn(status);
        when(chatModelService.resolve(isNull())).thenReturn(null);

        String result = tool.execute(context, Map.of("instruction", "新增节点"));

        assertTrue(result.contains(ErrorCodeConstants.AI_NOT_ENABLED.msg()));
        verify(testCaseNodeMapper, never()).listByDocumentId(any());
    }

    @Test
    void execute_emptyResponseReturnsError() {
        stubEnabledChatEnvironment(List.of());
        when(provider.complete(eq(model), anyList(), any())).thenReturn(
                new AiModels.ChatResult("", 10, 5, "stop"));

        String result = tool.execute(context, Map.of("instruction", "新增节点"));

        assertTrue(result.contains("脑图指令翻译未返回有效内容，请重试"));
    }

    @Test
    void execute_invalidJsonReturnsError() {
        stubEnabledChatEnvironment(List.of());
        when(provider.complete(eq(model), anyList(), any())).thenReturn(
                new AiModels.ChatResult("not-a-json", 10, 5, "stop"));

        String result = tool.execute(context, Map.of("instruction", "新增节点"));

        assertTrue(result.contains("脑图指令翻译结果解析失败"));
    }

    @Test
    void execute_ambiguousResultExposesClarification() {
        stubEnabledChatEnvironment(List.of());
        when(provider.complete(eq(model), anyList(), any())).thenReturn(new AiModels.ChatResult(
                "{\"commands\":[],\"ambiguous\":true,\"clarification\":\"请指明要修改哪个节点\"}",
                10, 5, "stop"));

        String result = tool.execute(context, Map.of("instruction", "改一下"));

        assertTrue(result.contains("请指明要修改哪个节点"));
    }

    @Test
    void execute_skeletonTruncatedWhenTooManyNodes() {
        List<TestCaseNode> manyNodes = new ArrayList<>();
        for (int i = 0; i < 350; i++) {
            TestCaseNode node = new TestCaseNode();
            node.setId(UUID.randomUUID());
            node.setTitle("节点" + i);
            node.setType("case");
            node.setParentId(i > 0 ? UUID.randomUUID() : null);
            manyNodes.add(node);
        }
        stubEnabledChatEnvironment(manyNodes);
        when(provider.complete(eq(model), anyList(), any())).thenReturn(new AiModels.ChatResult(
                "{\"commands\":[{\"action\":\"add_child\"}],\"ambiguous\":false,\"clarification\":null}",
                10, 5, "stop"));

        tool.execute(context, Map.of("instruction", "新增节点"));

        ArgumentCaptor<List<AiModels.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider).complete(eq(model), captor.capture(), any());
        String userContent = captor.getValue().get(1).content();
        assertTrue(userContent.contains("节点过多，仅列出前 300 条"));
        assertTrue(userContent.contains("【节点总数】350"));
    }

    private void stubEnabledChatEnvironment(List<TestCaseNode> nodes) {
        when(testCaseModuleMapper.selectById(documentId)).thenReturn(document());
        AiStatusRespDTO status = new AiStatusRespDTO();
        status.setEnabled(true);
        when(configService.getStatus()).thenReturn(status);
        when(chatModelService.resolve(isNull())).thenReturn(model);
        when(testCaseNodeMapper.listByDocumentId(documentId)).thenReturn(nodes);
        when(promptAssembler.assemble(eq(AiFunctionType.DSL_TRANSLATION), anyString(), anyString()))
                .thenAnswer(inv -> List.of(
                        AiModels.ChatMessage.system("s"),
                        AiModels.ChatMessage.user(inv.getArgument(2))));
    }

    private TestCaseModule document() {
        TestCaseModule document = new TestCaseModule();
        document.setId(documentId);
        document.setName("登录模块");
        return document;
    }
}
