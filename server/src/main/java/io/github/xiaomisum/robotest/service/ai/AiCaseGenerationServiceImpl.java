package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.AiModels.ChatCallOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class AiCaseGenerationServiceImpl implements AiCaseGenerationService {

    /** 结构上下文裁剪上限（详细设计 4.7：祖先路径与同级参照均超出取前 50） */
    static final int STRUCTURE_CONTEXT_LIMIT = 50;

    private static final String TASK_INSTRUCTION = """
            请基于业务数据中的需求内容，为脑图挂载位置生成一棵测试用例子树。
            业务数据中的「挂载位置上下文」描述了目标节点的祖先路径与既有子节点标题，\
            生成的顶层节点将作为目标节点的新子节点，请避免与既有子节点重复。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiOutputValidator outputValidator;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;

    @Override
    public SseEmitter generateCaseTree(UUID userId, UUID workspaceId, UUID projectId, AiCaseGenerateReqDTO reqDTO) {
        TestCaseModule document = requireDocument(reqDTO.getDocumentId(), projectId);
        List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(document.getId());
        TestCaseNode target = docNodes.stream()
                .filter(node -> node.getId().equals(reqDTO.getTargetNodeId()))
                .findFirst()
                .orElseThrow(() -> ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND));

        String businessData = buildBusinessData(reqDTO.getRequirementText(), target, docNodes);
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        return aiGatewayService.stream(context, AiFunctionType.CASE_GENERATION,
                TASK_INSTRUCTION, businessData, ChatCallOptions.json(), null, doneAssembler());
    }

    /** done 帧组装：结构化绑定 + 宽容规整（截断计 warnings）+ 自定义结构断言 */
    private Function<String, Object> doneAssembler() {
        return fullContent -> {
            AiNodeTreeDTO.Payload payload = outputValidator.parseAndValidate(
                    fullContent, AiNodeTreeDTO.Payload.class, null);
            List<String> warnings = AiNodeTreeAsserts.normalizeAndAssertTree(payload.getNodes());
            return Map.of("nodes", payload.getNodes(), "warnings", warnings);
        };
    }

    /** 文档必须存在、为 document 类型且属于当前项目（跨项目访问一律按不存在处理） */
    private TestCaseModule requireDocument(UUID documentId, UUID projectId) {
        TestCaseModule document = testCaseModuleMapper.selectById(documentId);
        if (document == null || !Constants.ModuleType.DOCUMENT.equals(document.getType())
                || !Objects.equals(document.getProjectId(), projectId)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    /**
     * 组装 Prompt 业务数据（详细设计 4.7）：需求文本 + 祖先路径标题链 + 目标节点直接子节点标题（同级参照）。
     * 业务数据由 PromptAssembler 统一置于防注入定界符内，超预算由其抛 1001。
     */
    private String buildBusinessData(String requirementText, TestCaseNode target, List<TestCaseNode> docNodes) {
        Map<UUID, TestCaseNode> nodeById = new HashMap<>();
        docNodes.forEach(node -> nodeById.put(node.getId(), node));

        List<String> ancestorTitles = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        TestCaseNode cursor = target;
        // 从目标节点向上回溯（visited 防脏数据成环），倒置为根到目标的路径链
        while (cursor != null && visited.add(cursor.getId())) {
            ancestorTitles.addFirst(cursor.getTitle());
            cursor = cursor.getParentId() != null ? nodeById.get(cursor.getParentId()) : null;
        }
        if (ancestorTitles.size() > STRUCTURE_CONTEXT_LIMIT) {
            ancestorTitles = ancestorTitles.subList(0, STRUCTURE_CONTEXT_LIMIT);
        }

        List<String> childTitles = docNodes.stream()
                .filter(node -> target.getId().equals(node.getParentId()))
                .sorted(Comparator.comparing(node -> node.getSortOrder() != null ? node.getSortOrder() : 0))
                .map(TestCaseNode::getTitle)
                .limit(STRUCTURE_CONTEXT_LIMIT)
                .toList();

        StringBuilder data = new StringBuilder();
        data.append("【需求内容】\n").append(requirementText).append('\n');
        data.append("【挂载位置上下文】\n");
        data.append("祖先路径：").append(String.join(" > ", ancestorTitles)).append('\n');
        if (!childTitles.isEmpty()) {
            data.append("目标节点既有子节点（新子树的同级参照，避免重复）：")
                    .append(String.join("、", childTitles)).append('\n');
        }
        return data.toString();
    }
}
