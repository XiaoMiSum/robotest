package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.service.ai.task.handler.CaseAssistSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** 用例优化提案清洗单测（C8）：只保留定位合法、字段 / 类型合法且建议非空的操作指令。 */
@ExtendWith(MockitoExtension.class)
class CaseOptimizeTypeAdapterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID STEP_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ID = UUID.randomUUID();

    @Mock
    private CaseAssistSupport assistSupport;
    @InjectMocks
    private CaseOptimizeTypeAdapter adapter;

    @Test
    void proposal_keepsValidOperationsAndDropsIllegalReferences() {
        TestCaseNode caseNode = node(CASE_ID, "登录用例", Constants.NodeType.CASE, null);
        caseNode.setPriority("P1");
        caseNode.setTags(List.of("登录"));
        TestCaseNode step = node(STEP_ID, "输入邮箱", Constants.NodeType.STEP, CASE_ID);
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                document, List.of(caseNode), Map.of(CASE_ID, caseNode, STEP_ID, step),
                Map.of(CASE_ID, List.of(step)));
        when(assistSupport.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).thenReturn(resolved);
        when(assistSupport.upstreamRequirements(PROJECT_ID, List.of())).thenReturn(List.of());

        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROJECT_ID,
                Map.of("documentId", DOCUMENT_ID.toString(), "nodeIds", List.of(CASE_ID.toString())), null));

        String output = """
                {"artifacts":[{"content":{"nodeId":"%s","operations":[
                  {"op":"update","nodeId":"%s","field":"title","suggested":"输入邮箱和密码","reason":"补充账号类型"},
                  {"op":"update","nodeId":"%s","field":"priority","suggested":"P0","reason":"上游要求"},
                  {"op":"update","nodeId":"%s","field":"tags","suggested":["登录","限制"],"reason":"补充标签"},
                  {"op":"add","parentId":"%s","nodeType":"step","title":"输入验证码","reason":"新增步骤"},
                  {"op":"update","nodeId":"%s","field":"title","suggested":"跨文档","reason":"非法"},
                  {"op":"add","parentId":"%s","nodeType":"case","title":"非法","reason":"非法"},
                  {"op":"update","nodeId":"%s","field":"priority","suggested":"P9","reason":"非法"}
                ]}}]}
                """.formatted(CASE_ID, STEP_ID, CASE_ID, CASE_ID, CASE_ID, FOREIGN_ID, CASE_ID, CASE_ID);

        List<ProposalDraft> drafts = preparation.proposalParser().apply(output);

        assertEquals(1, drafts.size());
        Map<String, Object> content = drafts.get(0).content();
        assertEquals(CASE_ID.toString(), content.get("nodeId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) content.get("operations");
        assertEquals(4, operations.size());
        assertEquals("输入邮箱和密码", operations.get(0).get("suggested"));
        assertEquals("输入邮箱", operations.get(0).get("existing"));
        assertEquals("登录用例 › 输入邮箱", operations.get(0).get("path"));
        assertEquals("P1", operations.get(1).get("existing"));
        assertEquals("P0", operations.get(1).get("suggested"));
        assertEquals(CASE_ID.toString(), operations.get(3).get("parentId"));
        assertEquals(Constants.NodeType.STEP, operations.get(3).get("nodeType"));
    }

    @Test
    void proposal_withoutAnyMatchedCase_failsModel() {
        TestCaseNode caseNode = node(CASE_ID, "登录用例", Constants.NodeType.CASE, null);
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        CaseAssistSupport.ResolvedCases resolved = new CaseAssistSupport.ResolvedCases(
                document, List.of(caseNode), Map.of(CASE_ID, caseNode), Map.of());
        when(assistSupport.resolve(PROJECT_ID, DOCUMENT_ID, List.of(CASE_ID))).thenReturn(resolved);
        when(assistSupport.upstreamRequirements(PROJECT_ID, List.of())).thenReturn(List.of());

        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROJECT_ID,
                Map.of("documentId", DOCUMENT_ID.toString(), "nodeIds", List.of(CASE_ID.toString())), null));

        String output = "{\"artifacts\":[{\"content\":{\"nodeId\":\"" + FOREIGN_ID + "\",\"operations\":[]}}]}";
        try {
            preparation.proposalParser().apply(output);
            org.junit.jupiter.api.Assertions.fail("未命中选中用例应按模型失败落态");
        } catch (xyz.migoo.framework.common.exception.ServiceException e) {
            assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), e.getCode());
        }
    }

    private static TestCaseNode node(UUID id, String title, String type, UUID parentId) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setTitle(title);
        node.setType(type);
        node.setParentId(parentId);
        return node;
    }
}
