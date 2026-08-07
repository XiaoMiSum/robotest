package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiCaseGenerateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiStepCompleteReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiTextImportReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiNodeTreeDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseModuleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.AiCallContext;
import io.github.xiaomisum.robotest.service.ai.model.AiModels.ChatCallOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

    private static final String COMPLETE_TASK_INSTRUCTION = """
            请为业务数据中描述的测试用例补全缺失的执行步骤与预期结果。
            「既有子节点」清单列出了该用例已有的前置/步骤/预期内容，已有步骤不重复输出；\
            输出为 step/expected 类型的扁平数组，仅包含需要新增的节点。""";

    private static final String IMPORT_TASK_INSTRUCTION = """
            请将业务数据中的外部文本解析为结构化的测试用例树。
            解析规则：制表符分隔的表格文本按「一行一用例」解析；Markdown 按标题层级映射模块分组；\
            自由文本按语义分组。无法识别为用例结构的内容归入 normal 节点，不得虚构原文没有的用例；\
            完全无法解析出用例结构时输出空的 nodes 数组。""";

    @Resource
    private AiGatewayService aiGatewayService;
    @Resource
    private AiOutputValidator outputValidator;
    @Resource
    private AiConfigService aiConfigService;
    @Resource
    private TestCaseModuleMapper testCaseModuleMapper;
    @Resource
    private TestCaseNodeMapper testCaseNodeMapper;
    @Resource
    private AiRequirementContextAssembler requirementContextAssembler;

    @Override
    public SseEmitter generateCaseTree(UUID userId, UUID workspaceId, UUID projectId, AiCaseGenerateReqDTO reqDTO) {
        TestCaseModule document = requireDocument(reqDTO.getDocumentId(), projectId);
        // 需求输入校验：手动文本与需求池条目至少一项非空（3.2.1）
        boolean hasText = StringUtils.hasText(reqDTO.getRequirementText());
        boolean hasItems = reqDTO.getRequirementIds() != null && !reqDTO.getRequirementIds().isEmpty();
        if (!hasText && !hasItems) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        List<String> contextWarnings = new ArrayList<>();
        // saveAsRequirement 非空时文本必填；生成开始前独立保存，保存失败不阻断生成（3.2.1）
        if (reqDTO.getSaveAsRequirement() != null) {
            if (!hasText) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
            }
            if (!requirementContextAssembler.trySaveRequirement(projectId, userId,
                    reqDTO.getSaveAsRequirement().getTitle(), reqDTO.getRequirementText())) {
                contextWarnings.add("临时需求保存为需求池条目失败，已跳过");
            }
        }

        List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(document.getId());
        TestCaseNode target = requireNodeInDocument(docNodes, reqDTO.getTargetNodeId());

        AiRequirementContextAssembler.RequirementContext reqCtx = requirementContextAssembler.assemble(projectId,
                reqDTO.getRequirementIds(), reqDTO.getRequirementText(), null);
        contextWarnings.addAll(reqCtx.warnings());
        String businessData = buildBusinessData(reqCtx.data(), target, docNodes);
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        return aiGatewayService.stream(context, AiFunctionType.CASE_GENERATION,
                TASK_INSTRUCTION, businessData, ChatCallOptions.json(), null, treeAssembler(false, contextWarnings));
    }

    @Override
    public SseEmitter completeSteps(UUID userId, UUID workspaceId, UUID projectId, AiStepCompleteReqDTO reqDTO) {
        TestCaseModule document = requireDocument(reqDTO.getDocumentId(), projectId);
        List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(document.getId());
        TestCaseNode target = requireNodeInDocument(docNodes, reqDTO.getNodeId());
        // 补全目标必须是 case 节点（3.2.2），其余类型按目标状态不允许处理
        if (!Constants.NodeType.CASE.equals(target.getType())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TARGET_STATE_INVALID);
        }

        AiRequirementContextAssembler.RequirementContext reqCtx = requirementContextAssembler.assemble(projectId,
                reqDTO.getRequirementIds(), reqDTO.getExtraText(), null);
        String businessData = buildCompleteBusinessData(reqCtx.data(), target, docNodes);
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        return aiGatewayService.stream(context, AiFunctionType.STEP_COMPLETION,
                COMPLETE_TASK_INSTRUCTION, businessData, ChatCallOptions.json(), null,
                flatStepsAssembler(reqCtx.warnings()));
    }

    @Override
    public SseEmitter importText(UUID userId, UUID workspaceId, UUID projectId, AiTextImportReqDTO reqDTO) {
        TestCaseModule document = requireDocument(reqDTO.getDocumentId(), projectId);
        List<TestCaseNode> docNodes = testCaseNodeMapper.listByDocumentId(document.getId());
        requireNodeInDocument(docNodes, reqDTO.getTargetNodeId());
        // 长度上限为系统配置项（默认 20000），超限按参数校验失败处理（3.2.3）
        if (reqDTO.getText().length() > aiConfigService.getIntSetting("importTextMaxLength")) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }

        String businessData = "【待解析文本】\n" + reqDTO.getText() + '\n';
        AiCallContext context = new AiCallContext(userId, workspaceId, projectId, reqDTO.getModelId());
        return aiGatewayService.stream(context, AiFunctionType.TEXT_IMPORT,
                IMPORT_TASK_INSTRUCTION, businessData, ChatCallOptions.json(), null, treeAssembler(true, List.of()));
    }

    /**
     * 完整树 done 帧组装：结构化绑定 + 宽容规整（截断计 warnings）+ 结构断言；
     * allowEmpty 时空树放行并追加"未能解析"提示（导入场景，设计 4.5）；
     * contextWarnings 为需求上下文组装产生的截断/另存提示，随 done 帧一并透出。
     */
    private Function<String, Object> treeAssembler(boolean allowEmpty, List<String> contextWarnings) {
        return fullContent -> {
            AiNodeTreeDTO.Payload payload = outputValidator.parseAndValidate(
                    fullContent, AiNodeTreeDTO.Payload.class, null);
            List<String> warnings = new ArrayList<>(contextWarnings);
            warnings.addAll(AiNodeTreeAsserts.normalizeAndAssertTree(payload.getNodes(), allowEmpty));
            List<AiNodeTreeDTO> nodes = payload.getNodes() != null ? payload.getNodes() : List.of();
            if (allowEmpty && nodes.isEmpty()) {
                warnings.add("未能解析出用例结构，请调整文本格式");
            }
            return Map.of("nodes", nodes, "warnings", warnings);
        };
    }

    /** 补全步骤 done 帧组装：step/expected 扁平数组断言，空数组表示无需补全 */
    private Function<String, Object> flatStepsAssembler(List<String> contextWarnings) {
        return fullContent -> {
            AiNodeTreeDTO.Payload payload = outputValidator.parseAndValidate(
                    fullContent, AiNodeTreeDTO.Payload.class, null);
            List<String> warnings = new ArrayList<>(contextWarnings);
            warnings.addAll(AiNodeTreeAsserts.normalizeAndAssertFlatSteps(payload.getNodes()));
            List<AiNodeTreeDTO> nodes = payload.getNodes() != null ? payload.getNodes() : List.of();
            return Map.of("nodes", nodes, "warnings", warnings);
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

    /** 目标节点必须属于该文档（节点清单已按文档加载，命中即归属成立） */
    private TestCaseNode requireNodeInDocument(List<TestCaseNode> docNodes, UUID nodeId) {
        return docNodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> ServiceExceptionUtil.get(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND));
    }

    /**
     * 组装 Prompt 业务数据（详细设计 4.7）：需求上下文 + 祖先路径标题链 + 目标节点直接子节点标题（同级参照）。
     * 业务数据由 PromptAssembler 统一置于防注入定界符内，超预算由其抛 1001。
     */
    private String buildBusinessData(String requirementData, TestCaseNode target, List<TestCaseNode> docNodes) {
        StringBuilder data = new StringBuilder();
        if (!requirementData.isBlank()) {
            data.append(requirementData);
        }
        data.append("【挂载位置上下文】\n");
        data.append("祖先路径：").append(String.join(" > ", ancestorTitles(target, docNodes))).append('\n');
        List<String> childTitles = childTitles(target, docNodes);
        if (!childTitles.isEmpty()) {
            data.append("目标节点既有子节点（新子树的同级参照，避免重复）：")
                    .append(String.join("、", childTitles)).append('\n');
        }
        return data.toString();
    }

    /**
     * 补全步骤上下文（3.2.2）：需求上下文 + 用例标题 + 祖先路径 + 同级节点标题（≤50）+ 既有子节点（去重参考）
     */
    private String buildCompleteBusinessData(String requirementData, TestCaseNode target, List<TestCaseNode> docNodes) {
        StringBuilder data = new StringBuilder();
        if (!requirementData.isBlank()) {
            data.append(requirementData);
        }
        data.append("【用例标题】").append(target.getTitle()).append('\n');
        data.append("【祖先路径】").append(String.join(" > ", ancestorTitles(target, docNodes))).append('\n');
        List<String> siblingTitles = docNodes.stream()
                .filter(node -> Objects.equals(node.getParentId(), target.getParentId())
                        && !node.getId().equals(target.getId()))
                .sorted(Comparator.comparing(node -> node.getSortOrder() != null ? node.getSortOrder() : 0))
                .map(TestCaseNode::getTitle)
                .limit(STRUCTURE_CONTEXT_LIMIT)
                .toList();
        if (!siblingTitles.isEmpty()) {
            data.append("【同级节点】").append(String.join("、", siblingTitles)).append('\n');
        }
        // 既有子节点全量列出（单用例子节点有限），供"已有步骤不重复输出"
        List<String> childLines = docNodes.stream()
                .filter(node -> target.getId().equals(node.getParentId()))
                .sorted(Comparator.comparing(node -> node.getSortOrder() != null ? node.getSortOrder() : 0))
                .map(node -> node.getType() + "：" + node.getTitle())
                .toList();
        if (!childLines.isEmpty()) {
            data.append("【既有子节点】\n").append(String.join("\n", childLines)).append('\n');
        }
        return data.toString();
    }

    /** 从目标节点向上回溯（visited 防脏数据成环），倒置为根到目标的路径链，超出裁剪至前 50 */
    private List<String> ancestorTitles(TestCaseNode target, List<TestCaseNode> docNodes) {
        Map<UUID, TestCaseNode> nodeById = new HashMap<>();
        docNodes.forEach(node -> nodeById.put(node.getId(), node));

        List<String> titles = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        TestCaseNode cursor = target;
        while (cursor != null && visited.add(cursor.getId())) {
            titles.addFirst(cursor.getTitle());
            cursor = cursor.getParentId() != null ? nodeById.get(cursor.getParentId()) : null;
        }
        return titles.size() > STRUCTURE_CONTEXT_LIMIT ? titles.subList(0, STRUCTURE_CONTEXT_LIMIT) : titles;
    }

    /** 目标节点直接子节点标题，按排序取前 50（4.7 裁剪策略） */
    private List<String> childTitles(TestCaseNode target, List<TestCaseNode> docNodes) {
        return docNodes.stream()
                .filter(node -> target.getId().equals(node.getParentId()))
                .sorted(Comparator.comparing(node -> node.getSortOrder() != null ? node.getSortOrder() : 0))
                .map(TestCaseNode::getTitle)
                .limit(STRUCTURE_CONTEXT_LIMIT)
                .toList();
    }
}
